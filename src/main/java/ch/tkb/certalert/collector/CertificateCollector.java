package ch.tkb.certalert.collector;

import ch.tkb.certalert.config.CertificateConfig;
import ch.tkb.certalert.metrics.CertificateMetricsPublisher;
import ch.tkb.certalert.model.CertificateInfo;
import ch.tkb.certalert.model.CertificateInfo.Status;
import ch.tkb.certalert.utils.KeystoreLoader;
import ch.tkb.certalert.utils.Resolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Collects certificate data and publishes metrics,
 * tracking state changes and pruning stale entries via the seen flag.
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
  public CertificateCollector(CertificateConfig config,
      CertificateMetricsPublisher metricsPublisher) {
    this.config = config;
    this.metricsPublisher = metricsPublisher;
    log.info("Initialized; monitoring {} certificates", config.certificates().size());
  }

  /**
   * Poll keystores, update metrics, and prune stale entries.
   */
  @Scheduled(fixedDelayString = "${certalert.check-interval}")
  public void collectCertificateData() {
    // Reset seen flag for all existingCert entries
    certificateInfos.forEach(ci -> ci.setSeen(false));

    // Process each configured keystore
    for (var entry : config.certificates()) {
      try {
        String pw = Resolver.resolve(entry.password());
        KeyStore ks = KeystoreLoader.load(entry.type(), entry.path(), pw);
        for (String alias : Collections.list(ks.aliases())) {
          processAlias(entry.name(), alias, ks);
        }
      } catch (Exception e) {
        handleLoadError(entry, e);
      }
    }

    // Remove entries not seen this cycle
    certificateInfos.removeIf(ci -> !ci.isSeen());
    lastUpdateTime = Instant.now();
  }

  /**
   * Return an immutable snapshot of the current certificate information.
   */
  public List<CertificateInfo> getCertificateInfos() {
    return List.copyOf(certificateInfos);
  }

  /**
   * Return the timestamp of the last data collection.
   */
  public Instant getLastUpdateTime() {
    return lastUpdateTime;
  }

  /**
   * Process a single certificate alias, logging and publishing metrics as needed.
   */
  private void processAlias(String name, String alias, KeyStore ks) {
    try {
      var newInfo = buildInfo(name, alias, ks);
      Optional<CertificateInfo> oldOpt = certificateInfos.stream()
          .filter(ci -> ci.getName().equals(name) && ci.getAlias().equals(alias))
          .peek(ci -> ci.setSeen(true))
          .findFirst();

      if (oldOpt.isPresent()) {
        var oldInfo = oldOpt.get();
        if (!newInfo.equals(oldInfo)) {
          log.info("Certificate {}:{} changed {} → {}",
              name, alias, oldInfo.getStatus(), newInfo.getStatus());
          int idx = certificateInfos.indexOf(oldInfo);
          certificateInfos.set(idx, newInfo);
          publishMetrics(newInfo);
        }
        return;
      }

      log.debug("New certificate {}:{} expires {}", name, alias, formatter.format(newInfo.getNotAfter()));
      certificateInfos.add(newInfo);
      publishMetrics(newInfo);

    } catch (Exception e) {
      handleAliasError(name, alias, e);
    }
  }

  /**
   * Publish expiration and validity metrics for a certificate.
   */
  private void publishMetrics(CertificateInfo info) {
    metricsPublisher.publishExpiration(info);
    metricsPublisher.publishValidity(
        info.getName(),
        info.getAlias(),
        info.getStatus() == Status.VALID);
  }

  /**
   * Handle errors loading or processing a specific alias.
   */
  private void handleAliasError(String name, String alias, Exception e) {
    metricsPublisher.publishValidity(name, alias, false);

    var errInfo = new CertificateInfo(name, alias, e.getMessage(), null, null, Status.INVALID);
    int idx = findCertIndex(name, alias);
    boolean certInList = idx >= 0;

    if (!certInList) {
      log.error("Error loading {}:{} {}", name, alias, e.getMessage());
      certificateInfos.add(errInfo);
      return;
    }

    var existingCert = certificateInfos.get(idx);
    existingCert.setSeen(true);
    if (!errInfo.equals(existingCert)) {
      log.warn("Error for {}:{} changed {}", name, alias, e.getMessage());
      certificateInfos.set(idx, errInfo);
    }
  }

  /**
   * Build a CertificateInfo for the given name and alias.
   */
  private CertificateInfo buildInfo(String name, String alias, KeyStore ks) throws Exception {
    X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
    if (cert == null) {
      return new CertificateInfo(name, alias, "certificate is missing", null, null, Status.INVALID);
    }
    Instant nb = cert.getNotBefore().toInstant();
    Instant na = cert.getNotAfter().toInstant();
    Status status = Instant.now().isAfter(na) ? Status.EXPIRED : Status.VALID;
    return new CertificateInfo(name, alias, cert.getSubjectX500Principal().getName(), nb, na, status);
  }

  /**
   * Find the index of a CertificateInfo by name and alias.
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

  /**
   * Handle errors loading an entire keystore entry.
   */
  private void handleLoadError(CertificateConfig.CertificateEntry entry, Exception e) {
    handleAliasError(entry.name(), "unknown", e);
  }
}
