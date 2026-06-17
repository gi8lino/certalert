package ch.tkb.certalert.collector;

import ch.tkb.certalert.config.CertificateConfig;
import ch.tkb.certalert.metrics.CertificateMetricsPublisher;
import ch.tkb.certalert.model.CertificateInfo;
import ch.tkb.certalert.model.CertificateInfo.Status;
import ch.tkb.certalert.utils.CertificateLoader;
import ch.tkb.certalert.utils.KeystoreLoader;
import ch.tkb.certalert.utils.Resolver;
import java.io.File;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Collects certificate data and publishes metrics, tracking state changes and replacing stale
 * entries with a fresh snapshot.
 */
@Component
public class CertificateCollector {

  private static final Logger log = LoggerFactory.getLogger(CertificateCollector.class);
  private static final DateTimeFormatter formatter =
      DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

  private final CertificateConfig config;
  private final CertificateMetricsPublisher metricsPublisher;

  /** Holds the last collected certificate information. */
  private final AtomicReference<List<CertificateInfo>> certificateInfos =
      new AtomicReference<>(List.of());

  private Instant lastUpdateTime;

  /** Construct a CertificateCollector with config and metrics publisher. */
  public CertificateCollector(
      CertificateConfig config, CertificateMetricsPublisher metricsPublisher) {
    this.config = config;
    this.metricsPublisher = metricsPublisher;
    log.info("Initialized; monitoring {} certificates", config.certificates().size());
  }

  /**
   * Scheduled polling method. Polls all configured keystores or certs, publishes metrics, and
   * prunes stale entries.
   */
  @Scheduled(fixedDelayString = "${certalert.check-interval}")
  public void collectCertificateData() {
    Map<String, CertificateInfo> existing = indexByIdentity(certificateInfos.get());
    List<CertificateInfo> collected = new ArrayList<>();

    // Process each configured certificate source
    for (var entry : config.certificates()) {
      try {
        switch (entry.type().toLowerCase()) {
          case "pem", "crt" -> {
            List<X509Certificate> certs = CertificateLoader.loadAll(entry.path());
            for (int i = 0; i < certs.size(); i++) {
              String alias = certs.size() == 1 ? "default" : "cert" + (i + 1);
              collected.add(
                  processSingleCert(
                      entry.path(), entry.type(), entry.name(), alias, certs.get(i), existing));
            }
          }
          case "jceks", "jks", "dks", "p12", "pkcs11", "pkcs12" -> {
            String pw = Resolver.resolve(entry.password());
            KeyStore ks = KeystoreLoader.load(entry.type(), entry.path(), pw);
            for (String alias : Collections.list(ks.aliases())) {
              collected.add(
                  processAlias(entry.path(), entry.type(), entry.name(), alias, ks, existing));
            }
          }
          default ->
              throw new IllegalArgumentException("Unsupported certificate type: " + entry.type());
        }
      } catch (Exception e) {
        collected.add(handleLoadError(entry, e, existing));
      }
    }

    certificateInfos.set(List.copyOf(collected));
    metricsPublisher.prune(collected);
    lastUpdateTime = Instant.now();
  }

  /** Return a snapshot of current certificate info. */
  public List<CertificateInfo> getCertificateInfos() {
    return certificateInfos.get();
  }

  /** Return the timestamp of the last scan. */
  public Instant getLastUpdateTime() {
    return lastUpdateTime;
  }

  /** Handle X.509 cert directly (non-keystore). */
  private CertificateInfo processSingleCert(
      String path,
      String type,
      String name,
      String alias,
      X509Certificate cert,
      Map<String, CertificateInfo> existing) {
    try {
      var newInfo = buildInfoFromCert(path, type, name, alias, cert);
      Optional<CertificateInfo> oldInfo =
          Optional.ofNullable(existing.get(identityKey(path, type, name, alias)));

      if (oldInfo.isPresent()) {
        var old = oldInfo.get();
        if (!newInfo.equals(old)) {
          log.info(
              "Certificate {}:{} changed {} → {}",
              name,
              alias,
              old.getStatus(),
              newInfo.getStatus());
          publishMetrics(newInfo);
        }
        return newInfo;
      }

      log.debug(
          "New certificate {}:{} expires {}", name, alias, formatter.format(newInfo.getNotAfter()));
      publishMetrics(newInfo);
      return newInfo;
    } catch (Exception e) {
      return handleAliasError(path, type, name, alias, e, existing);
    }
  }

  /** Handle alias inside a keystore. */
  private CertificateInfo processAlias(
      String path,
      String type,
      String name,
      String alias,
      KeyStore ks,
      Map<String, CertificateInfo> existing) {
    try {
      var newInfo = buildInfo(path, type, name, alias, ks);
      Optional<CertificateInfo> oldInfo =
          Optional.ofNullable(existing.get(identityKey(path, type, name, alias)));

      if (oldInfo.isPresent()) {
        var old = oldInfo.get();
        if (!newInfo.equals(old)) {
          log.info(
              "Certificate {}:{} changed {} → {}",
              name,
              alias,
              old.getStatus(),
              newInfo.getStatus());
          publishMetrics(newInfo);
        }
        return newInfo;
      }

      log.debug(
          "New certificate {}:{} expires {}", name, alias, formatter.format(newInfo.getNotAfter()));
      publishMetrics(newInfo);
      return newInfo;
    } catch (Exception e) {
      return handleAliasError(path, type, name, alias, e, existing);
    }
  }

  /** Extracts X509 info from a single certificate. */
  private CertificateInfo buildInfoFromCert(
      String path, String type, String name, String alias, X509Certificate cert) {
    Instant nb = cert.getNotBefore().toInstant();
    Instant na = cert.getNotAfter().toInstant();
    Status status = Instant.now().isAfter(na) ? Status.EXPIRED : Status.VALID;
    File f = new File(path);
    String fileName = f.getName();

    return CertificateInfo.builder()
        .path(path)
        .fileName(fileName)
        .name(name)
        .type(type)
        .alias(alias)
        .subject(cert.getSubjectX500Principal().getName())
        .notBefore(nb)
        .notAfter(na)
        .status(status)
        .build();
  }

  /** Extracts X509 info from a keystore alias. */
  private CertificateInfo buildInfo(
      String path, String type, String name, String alias, KeyStore ks) throws Exception {
    X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
    if (cert == null) {
      return CertificateInfo.builder()
          .path(path)
          .name(name)
          .type(type)
          .alias(alias)
          .subject("certificate is missing")
          .notBefore(null)
          .notAfter(null)
          .status(Status.INVALID)
          .build();
    }
    return buildInfoFromCert(path, type, name, alias, cert);
  }

  /** Publishes metrics for a certificate. */
  private void publishMetrics(CertificateInfo info) {
    metricsPublisher.publishExpiration(info);
    metricsPublisher.publishValidity(info, info.getStatus() == Status.VALID);
  }

  /** Handles alias-level load/parse errors. */
  private CertificateInfo handleAliasError(
      String path,
      String type,
      String name,
      String alias,
      Exception e,
      Map<String, CertificateInfo> existing) {
    var errInfo =
        CertificateInfo.builder()
            .path(path)
            .name(name)
            .type(type)
            .alias(alias)
            .subject(e.getMessage())
            .status(Status.INVALID)
            .build();
    metricsPublisher.publishValidity(errInfo, false);

    CertificateInfo oldInfo = existing.get(identityKey(path, type, name, alias));
    if (oldInfo != null) {
      if (!errInfo.equals(oldInfo)) {
        log.warn("Error for {}:{} changed {}", name, alias, e.getMessage());
      }
      return errInfo;
    }
    log.error("Error loading {}:{} {}", name, alias, e.getMessage());
    return errInfo;
  }

  /** Handles total keystore load failure. */
  private CertificateInfo handleLoadError(
      CertificateConfig.CertificateEntry entry,
      Exception e,
      Map<String, CertificateInfo> existing) {
    return handleAliasError(entry.path(), entry.type(), entry.name(), "unknown", e, existing);
  }

  /** Builds a lookup of previously collected certificates. */
  private Map<String, CertificateInfo> indexByIdentity(List<CertificateInfo> infos) {
    Map<String, CertificateInfo> index = new HashMap<>();
    for (CertificateInfo info : infos) {
      index.put(identityKey(info.getPath(), info.getType(), info.getName(), info.getAlias()), info);
    }
    return index;
  }

  private String identityKey(String path, String type, String name, String alias) {
    return path + "|" + type + "|" + name + "|" + alias;
  }
}
