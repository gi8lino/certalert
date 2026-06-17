package ch.tkb.certalert.collector;

import ch.tkb.certalert.config.CertificateConfig;
import ch.tkb.certalert.metrics.CertificateMetricsPublisher;
import ch.tkb.certalert.model.CertificateIdentity;
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

  private final AtomicReference<Instant> lastUpdateTime = new AtomicReference<>();

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
    Map<CertificateIdentity, CertificateInfo> existing = indexByIdentity(certificateInfos.get());
    List<CertificateInfo> collected = new ArrayList<>();

    // Process each configured certificate source
    for (var entry : config.certificates()) {
      try {
        switch (entry.type().toLowerCase()) {
          case "pem", "crt" -> {
            List<X509Certificate> certs = CertificateLoader.loadAll(entry.path());
            for (int i = 0; i < certs.size(); i++) {
              String alias = certs.size() == 1 ? "default" : "cert" + (i + 1);
              collected.add(processInfo(buildInfoFromCert(entry, alias, certs.get(i)), existing));
            }
          }
          case "jceks", "jks", "dks", "p12", "pkcs11", "pkcs12" -> {
            String pw = Resolver.resolve(entry.password());
            KeyStore ks = KeystoreLoader.load(entry.type(), entry.path(), pw);
            for (String alias : Collections.list(ks.aliases())) {
              collected.add(processAlias(entry, alias, ks, existing));
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
    lastUpdateTime.set(Instant.now());
  }

  /** Return a snapshot of current certificate info. */
  public List<CertificateInfo> getCertificateInfos() {
    return certificateInfos.get();
  }

  /** Return the timestamp of the last scan. */
  public Instant getLastUpdateTime() {
    return lastUpdateTime.get();
  }

  /** Handle alias inside a keystore. */
  private CertificateInfo processAlias(
      CertificateConfig.CertificateEntry entry,
      String alias,
      KeyStore ks,
      Map<CertificateIdentity, CertificateInfo> existing) {
    try {
      return processInfo(buildInfo(entry.path(), entry.type(), entry.name(), alias, ks), existing);
    } catch (Exception e) {
      return handleAliasError(entry.path(), entry.type(), entry.name(), alias, e, existing);
    }
  }

  /** Publishes and logs changes for a collected certificate. */
  private CertificateInfo processInfo(
      CertificateInfo newInfo, Map<CertificateIdentity, CertificateInfo> existing) {
    CertificateInfo oldInfo = existing.get(CertificateIdentity.from(newInfo));

    if (oldInfo != null) {
      if (!newInfo.equals(oldInfo)) {
        log.info(
            "Certificate {}:{} changed {} → {}",
            newInfo.getName(),
            newInfo.getAlias(),
            oldInfo.getStatus(),
            newInfo.getStatus());
        publishMetrics(newInfo);
      }
      return newInfo;
    }

    logNewCertificate(newInfo);
    publishMetrics(newInfo);
    return newInfo;
  }

  private void logNewCertificate(CertificateInfo info) {
    if (info.getNotAfter() == null) {
      log.debug("New certificate {}:{} has no expiry date", info.getName(), info.getAlias());
      return;
    }
    log.debug(
        "New certificate {}:{} expires {}",
        info.getName(),
        info.getAlias(),
        formatter.format(info.getNotAfter()));
  }

  private CertificateInfo buildInfoFromCert(
      CertificateConfig.CertificateEntry entry, String alias, X509Certificate cert) {
    return buildInfoFromCert(entry.path(), entry.type(), entry.name(), alias, cert);
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
    try {
      metricsPublisher.publishExpiration(info);
      metricsPublisher.publishValidity(info, info.getStatus() == Status.VALID);
    } catch (RuntimeException e) {
      log.warn(
          "Failed to publish metrics for {}:{}: {}",
          info.getName(),
          info.getAlias(),
          e.getMessage());
    }
  }

  /** Handles alias-level load/parse errors. */
  private CertificateInfo handleAliasError(
      String path,
      String type,
      String name,
      String alias,
      Exception e,
      Map<CertificateIdentity, CertificateInfo> existing) {
    var errInfo =
        CertificateInfo.builder()
            .path(path)
            .name(name)
            .type(type)
            .alias(alias)
            .subject(e.getMessage())
            .status(Status.INVALID)
            .build();
    publishMetrics(errInfo);

    CertificateInfo oldInfo = existing.get(new CertificateIdentity(path, type, name, alias));
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
      Map<CertificateIdentity, CertificateInfo> existing) {
    return handleAliasError(entry.path(), entry.type(), entry.name(), "unknown", e, existing);
  }

  /** Builds a lookup of previously collected certificates. */
  private Map<CertificateIdentity, CertificateInfo> indexByIdentity(List<CertificateInfo> infos) {
    Map<CertificateIdentity, CertificateInfo> index = new HashMap<>();
    for (CertificateInfo info : infos) {
      index.put(CertificateIdentity.from(info), info);
    }
    return index;
  }
}
