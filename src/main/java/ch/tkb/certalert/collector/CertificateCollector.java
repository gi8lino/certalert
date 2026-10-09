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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Collects certificate data, logs state changes, and exposes the latest immutable snapshot. */
@Component
public class CertificateCollector {

  /** Logger for collection activity and errors. */
  private static final Logger log = LoggerFactory.getLogger(CertificateCollector.class);

  /** Formatter used to log certificate expiry timestamps in the configured time zone. */
  private final DateTimeFormatter formatter;

  /** Source configuration for certificate collection. */
  private final CertificateConfig config;

  /** Publisher that reconciles Prometheus metrics after each scan. */
  private final CertificateMetricsPublisher metricsPublisher;

  /** Holds the last collected certificate information. */
  private final AtomicReference<List<CertificateInfo>> certificateInfos =
      new AtomicReference<>(List.of());

  /** Holds the completion time of the latest collection cycle. */
  private final AtomicReference<Instant> lastUpdateTime = new AtomicReference<>();

  /** Construct a CertificateCollector with config and metrics publisher. */
  public CertificateCollector(
      CertificateConfig config, CertificateMetricsPublisher metricsPublisher) {
    this.config = config;
    this.metricsPublisher = metricsPublisher;
    this.formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(config.dashboard().zoneId());
    log.info("Initialized; monitoring {} certificates", config.certificates().size());
  }

  /** Scheduled polling method that replaces the certificate snapshot and reconciles its metrics. */
  @Scheduled(fixedDelayString = "${certalert.check-interval}")
  public void collectCertificateData() {
    Map<CertificateIdentity, CertificateInfo> existing = indexByIdentity(certificateInfos.get());
    List<CertificateInfo> collected = new ArrayList<>();

    for (var entry : config.certificates()) {
      collected.addAll(collectEntry(entry, existing));
    }

    List<CertificateInfo> snapshot = List.copyOf(collected);
    certificateInfos.set(snapshot);
    publishMetrics(snapshot);
    lastUpdateTime.set(Instant.now());
  }

  /** Collects all certificates represented by one configured certificate source. */
  private List<CertificateInfo> collectEntry(
      CertificateConfig.CertificateEntry entry,
      Map<CertificateIdentity, CertificateInfo> existing) {
    try {
      return switch (entry.type().toLowerCase(Locale.ROOT)) {
        case "pem", "crt" -> collectPemCertificates(entry, existing);
        case "jceks", "jks", "dks", "p12", "pkcs11", "pkcs12" ->
            collectKeystoreCertificates(entry, existing);
        default ->
            throw new IllegalArgumentException("Unsupported certificate type: " + entry.type());
      };
    } catch (Exception e) {
      return List.of(handleLoadError(entry, e, existing));
    }
  }

  /** Collects every X.509 certificate in a PEM or CRT bundle. */
  private List<CertificateInfo> collectPemCertificates(
      CertificateConfig.CertificateEntry entry, Map<CertificateIdentity, CertificateInfo> existing)
      throws Exception {
    List<X509Certificate> certificates = CertificateLoader.loadAll(entry.path());
    List<CertificateInfo> collected = new ArrayList<>();
    for (int index = 0; index < certificates.size(); index++) {
      String alias = certificates.size() == 1 ? "default" : "cert" + (index + 1);
      collected.add(
          processInfo(buildInfoFromCert(entry, alias, certificates.get(index)), existing));
    }
    return collected;
  }

  /** Collects every alias in a configured keystore. */
  private List<CertificateInfo> collectKeystoreCertificates(
      CertificateConfig.CertificateEntry entry, Map<CertificateIdentity, CertificateInfo> existing)
      throws Exception {
    String password = Resolver.resolve(entry.password());
    KeyStore keyStore = KeystoreLoader.load(entry.type(), entry.path(), password);
    List<CertificateInfo> collected = new ArrayList<>();
    for (String alias : Collections.list(keyStore.aliases())) {
      collected.add(processAlias(entry, alias, keyStore, existing));
    }
    return collected;
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
      }
      return newInfo;
    }

    logNewCertificate(newInfo);
    return newInfo;
  }

  /** Logs the expiry date of a newly observed certificate when one is available. */
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

  /** Builds certificate information from a PEM or CRT configuration entry. */
  private CertificateInfo buildInfoFromCert(
      CertificateConfig.CertificateEntry entry, String alias, X509Certificate cert) {
    return buildInfoFromCert(entry.path(), entry.type(), entry.name(), alias, cert);
  }

  /** Extracts X509 info from a single certificate. */
  private CertificateInfo buildInfoFromCert(
      String path, String type, String name, String alias, X509Certificate cert) {
    Instant nb = cert.getNotBefore().toInstant();
    Instant na = cert.getNotAfter().toInstant();
    Status status = determineStatus(Instant.now(), nb, na);
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

  /** Determines whether a certificate is valid, not yet valid, or expired at the given instant. */
  static Status determineStatus(Instant currentTime, Instant notBefore, Instant notAfter) {
    if (currentTime.isBefore(notBefore)) {
      return Status.INVALID;
    }
    return currentTime.isBefore(notAfter) ? Status.VALID : Status.EXPIRED;
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

  /** Reconciles metrics and contains publisher failures so a scan still completes. */
  private void publishMetrics(List<CertificateInfo> infos) {
    try {
      metricsPublisher.publish(infos);
    } catch (RuntimeException e) {
      log.warn("Failed to reconcile certificate metrics", e);
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
            .subject(describe(e))
            .status(Status.INVALID)
            .build();
    CertificateInfo oldInfo = existing.get(new CertificateIdentity(path, type, name, alias));
    if (oldInfo != null) {
      if (!errInfo.equals(oldInfo)) {
        log.warn("Certificate {}:{} became invalid: {}", name, alias, describe(e), e);
      }
      return errInfo;
    }
    log.error("Unable to load certificate {}:{}: {}", name, alias, describe(e), e);
    return errInfo;
  }

  /** Returns a non-empty, human-readable description of a collection failure. */
  private String describe(Exception exception) {
    String message = exception.getMessage();
    return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
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
