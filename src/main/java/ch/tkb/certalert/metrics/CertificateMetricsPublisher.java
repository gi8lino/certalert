package ch.tkb.certalert.metrics;

import ch.tkb.certalert.model.CertificateInfo;
import com.google.common.util.concurrent.AtomicDouble;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Publishes certificate metrics to the configured MeterRegistry.
 */
@Component
public class CertificateMetricsPublisher {

  private final MeterRegistry meterRegistry;

  /**
   * Holds registered expiration metrics keyed by certificate identity.
   */
  private final ConcurrentMap<MetricKey, MetricState> certExpirationMetrics = new ConcurrentHashMap<>();

  /**
   * Holds registered validity metrics keyed by certificate identity.
   */
  private final ConcurrentMap<MetricKey, MetricState> certValidityMetrics = new ConcurrentHashMap<>();

  /**
   * Initializes the publisher with a MeterRegistry.
   */
  public CertificateMetricsPublisher(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  /**
   * Publishes or updates the expiration metric for a given certificate.
   */
  public void publishExpiration(CertificateInfo certInfo) {
    Instant expiry = certInfo.getNotAfter();
    if (expiry == null) {
      return;
    }

    long epochSeconds = expiry.getEpochSecond();
    MetricKey key = MetricKey.from(certInfo);

    certExpirationMetrics.computeIfAbsent(key, metricKey -> {
      AtomicDouble holder = new AtomicDouble(epochSeconds);
      Gauge gauge = Gauge.builder("certalert_certificate_expiration_seconds", holder, AtomicDouble::get)
          .description("Certificate expiration time in epoch seconds")
          .tags(
              "certificate_name", certInfo.getName(),
              "alias", certInfo.getAlias(),
              "path", certInfo.getPath(),
              "type", certInfo.getType())
          .register(meterRegistry);
      return new MetricState(holder, gauge);
    }).holder().set(epochSeconds);
  }

  /**
   * Publishes or updates the validity metric for a given certificate.
   */
  public void publishValidity(CertificateInfo certInfo, boolean isValid) {
    MetricKey key = MetricKey.from(certInfo);
    double status = isValid ? 0 : 1;

    certValidityMetrics.computeIfAbsent(key, metricKey -> {
      AtomicDouble holder = new AtomicDouble(status);
      Gauge gauge = Gauge.builder("certalert_certificate_validity", holder, AtomicDouble::get)
          .description("Indicates if a certificate is valid (0 = valid, 1 = invalid)")
          .tags(
              "certificate_name", certInfo.getName(),
              "alias", certInfo.getAlias(),
              "path", certInfo.getPath(),
              "type", certInfo.getType())
          .register(meterRegistry);
      return new MetricState(holder, gauge);
    }).holder().set(status);
  }

  /**
   * Removes metrics for certificate identities that are no longer present.
   */
  public void prune(Collection<CertificateInfo> activeCertificates) {
    Set<MetricKey> activeKeys = activeCertificates.stream()
        .map(MetricKey::from)
        .collect(Collectors.toSet());
    Set<MetricKey> expiringKeys = activeCertificates.stream()
        .filter(certInfo -> certInfo.getNotAfter() != null)
        .map(MetricKey::from)
        .collect(Collectors.toSet());

    removeInactive(certValidityMetrics, activeKeys);
    removeInactive(certExpirationMetrics, expiringKeys);
  }

  private void removeInactive(ConcurrentMap<MetricKey, MetricState> metrics, Set<MetricKey> activeKeys) {
    metrics.entrySet().removeIf(entry -> {
      if (activeKeys.contains(entry.getKey())) {
        return false;
      }
      meterRegistry.remove(entry.getValue().gauge());
      return true;
    });
  }

  private record MetricKey(String path, String type, String certName, String alias) {
    private static MetricKey from(CertificateInfo certInfo) {
      return new MetricKey(certInfo.getPath(), certInfo.getType(), certInfo.getName(), certInfo.getAlias());
    }
  }

  private record MetricState(AtomicDouble holder, Meter gauge) {
  }
}
