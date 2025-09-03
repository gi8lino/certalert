package ch.tkb.certalert.collector;

import ch.tkb.certalert.config.CertificateConfig;
import ch.tkb.certalert.metrics.CertificateMetricsPublisher;
import ch.tkb.certalert.model.CertificateInfo;
import ch.tkb.certalert.model.CertificateInfo.Status;
import ch.tkb.certalert.utils.CertificateLoader;
import ch.tkb.certalert.utils.KeystoreLoader;
import ch.tkb.certalert.utils.Resolver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.security.cert.X509Certificate;
import java.security.KeyStore;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Collects certificate data and publishes metrics,
 * tracking state changes and pruning stale entries via a "seen" flag.
 */
@Component
public class CertificateCollector {

  private static final Logger log = LoggerFactory.getLogger(CertificateCollector.class);
  private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
      .withZone(ZoneId.systemDefault());

  private final CertificateConfig config;
  private final CertificateMetricsPublisher metricsPublisher;
  /**
   * Holds the last collected certificate information.
   */
  private final List<CertificateInfo> certificateInfos = new ArrayList<>();
  private Instant lastUpdateTime;

  /**
   * Construct a CertificateCollector with config and metrics publisher.
   */
  public CertificateCollector(CertificateConfig config, CertificateMetricsPublisher metricsPublisher) {
    this.config = config;
    this.metricsPublisher = metricsPublisher;
    log.info("Initialized; monitoring {} certificates", config.certificates().size());
  }

  /**
   * Scheduled polling method.
   * Polls all configured keystores or certs, publishes metrics, and prunes stale
   * entries.
   */
  @Scheduled(fixedDelayString = "${certalert.check-interval}")
  public void collectCertificateData() {
    // Mark all certs as unseen at the beginning of this cycle
    certificateInfos.forEach(ci -> ci.setSeen(false));

    // // Process each configured certificate source
    for (var entry : config.certificates()) {
      try {
        switch (entry.type().toLowerCase()) {
          case "pem", "crt" -> {
            List<X509Certificate> certs = CertificateLoader.loadAll(entry.path());
            for (int i = 0; i < certs.size(); i++) {
              String alias = certs.size() == 1 ? "default" : "cert" + (i + 1);
              processSingleCert(entry.path(), entry.type(), entry.name(), alias, certs.get(i));
            }
          }
          case "jceks", "jks", "dks", "p12", "pkcs11", "pkcs12" -> {
            String pw = Resolver.resolve(entry.password());
            KeyStore ks = KeystoreLoader.load(entry.type(), entry.path(), pw);
            for (String alias : Collections.list(ks.aliases())) {
              processAlias(entry.path(), entry.type(), entry.name(), alias, ks);
            }
          }
          default -> throw new IllegalArgumentException("Unsupported certificate type: " + entry.type());
        }
      } catch (Exception e) {
        handleLoadError(entry, e);
      }
    }

    // Prune stale certs that were not seen in this run
    certificateInfos.removeIf(ci -> !ci.isSeen());
    lastUpdateTime = Instant.now();
  }

  /**
   * Return a snapshot of current certificate info.
   */
  public List<CertificateInfo> getCertificateInfos() {
    return List.copyOf(certificateInfos);
  }

  /**
   * Return the timestamp of the last scan.
   */
  public Instant getLastUpdateTime() {
    return lastUpdateTime;
  }

  /**
   * Handle X.509 cert directly (non-keystore).
   */
  private void processSingleCert(String path, String type, String name, String alias, X509Certificate cert) {
    try {
      var newInfo = buildInfoFromCert(path, type, name, alias, cert);
      Optional<CertificateInfo> existing = certificateInfos.stream()
          .filter(ci -> ci.getName().equals(name) && ci.getAlias().equals(alias))
          .peek(ci -> ci.setSeen(true))
          .findFirst();

      if (existing.isPresent()) {
        var old = existing.get();
        if (!newInfo.equals(old)) {
          log.info("Certificate {}:{} changed {} → {}", name, alias, old.getStatus(), newInfo.getStatus());
          certificateInfos.set(certificateInfos.indexOf(old), newInfo);
          publishMetrics(newInfo);
        }
        return;
      }

      log.debug("New certificate {}:{} expires {}", name, alias, formatter.format(newInfo.getNotAfter()));
      certificateInfos.add(newInfo);
      publishMetrics(newInfo);
    } catch (Exception e) {
      handleAliasError(path, type, name, alias, e);
    }
  }

  /**
   * Handle alias inside a keystore.
   */
  private void processAlias(String path, String type, String name, String alias, KeyStore ks) {
    try {
      var newInfo = buildInfo(path, type, name, alias, ks);
      Optional<CertificateInfo> existing = certificateInfos.stream()
          .filter(ci -> ci.getName().equals(name) && ci.getAlias().equals(alias))
          .peek(ci -> ci.setSeen(true))
          .findFirst();

      if (existing.isPresent()) {
        var old = existing.get();
        if (!newInfo.equals(old)) {
          log.info("Certificate {}:{} changed {} → {}", name, alias, old.getStatus(), newInfo.getStatus());
          certificateInfos.set(certificateInfos.indexOf(old), newInfo);
          publishMetrics(newInfo);
        }
        return;
      }

      log.debug("New certificate {}:{} expires {}", name, alias, formatter.format(newInfo.getNotAfter()));
      certificateInfos.add(newInfo);
      publishMetrics(newInfo);
    } catch (Exception e) {
      handleAliasError(path, type, name, alias, e);
    }
  }

  /**
   * Extracts X509 info from a single certificate.
   */
  private CertificateInfo buildInfoFromCert(String path, String type, String name, String alias, X509Certificate cert) {
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

  /**
   * Extracts X509 info from a keystore alias.
   */
  private CertificateInfo buildInfo(String path, String type, String name, String alias, KeyStore ks) throws Exception {
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

  /**
   * Publishes metrics for a certificate.
   */
  private void publishMetrics(CertificateInfo info) {
    metricsPublisher.publishExpiration(info);
    metricsPublisher.publishValidity(info.getPath(), info.getName(), info.getAlias(), info.getStatus() == Status.VALID);
  }

  /**
   * Handles alias-level load/parse errors.
   */
  private void handleAliasError(String path, String type, String name, String alias, Exception e) {
    metricsPublisher.publishValidity(path, name, alias, false);
    var errInfo = CertificateInfo.builder()
        .path(path)
        .name(name)
        .type(type)
        .alias(alias)
        .subject(e.getMessage())
        .status(Status.INVALID)
        .build();

    int idx = findCertIndex(name, alias);
    if (idx >= 0) {
      certificateInfos.get(idx).setSeen(true);
      if (!errInfo.equals(certificateInfos.get(idx))) {
        log.warn("Error for {}:{} changed {}", name, alias, e.getMessage());
        certificateInfos.set(idx, errInfo);
      }
      return;
    }
    log.error("Error loading {}:{} {}", name, alias, e.getMessage());
    certificateInfos.add(errInfo);
  }

  /**
   * Handles total keystore load failure.
   */
  private void handleLoadError(CertificateConfig.CertificateEntry entry, Exception e) {
    handleAliasError(entry.path(), entry.type(), entry.name(), "unknown", e);
  }

  /**
   * Finds index of cert by name and alias.
   */
  private int findCertIndex(String name, String alias) {
    for (int i = 0; i < certificateInfos.size(); i++) {
      var ci = certificateInfos.get(i);
      if (ci.getName().equals(name) && ci.getAlias().equals(alias)) {
        return i;
      }
    }
    return -1;
  }
}
