package ch.tkb.certalert.metrics;

import ch.tkb.certalert.model.CertificateInfo;
import com.google.common.util.concurrent.AtomicDouble;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Publishes certificate metrics to the configured MeterRegistry.
 */
@Component
public class CertificateMetricsPublisher {

  private final MeterRegistry meterRegistry;

  /**
   * Holds registered expiration metrics keyed by certificate name and alias.
   */
  private final Map<String, AtomicDouble> certExpirationMetrics = new HashMap<>();

  /**
   * Holds registered validity metrics keyed by certificate name and alias.
   */
  private final Map<String, AtomicDouble> certValidityMetrics = new HashMap<>();

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
    long epochSeconds = expiry.getEpochSecond();
    String aliasKey = certInfo.getName() + "|" + certInfo.getAlias();

    certExpirationMetrics.computeIfAbsent(aliasKey, k -> {
      AtomicDouble holder = new AtomicDouble(epochSeconds);
      Gauge.builder("certalert_certificate_expiration_seconds", holder, AtomicDouble::get)
          .description("Certificate expiration time in epoch seconds")
          .tags("certificate_name", certInfo.getName(), "alias", certInfo.getAlias())
          .register(meterRegistry);
      return holder;
    }).set(epochSeconds);
  }

  /**
   * Publishes or updates the validity metric for a given certificate.
   */
  public void publishValidity(String certName, String alias, boolean isValid) {
    String aliasKey = certName + "|" + alias;
    double value = isValid ? 0 : 1;

    certValidityMetrics.computeIfAbsent(aliasKey, k -> {
      AtomicDouble holder = new AtomicDouble(value);
      Gauge.builder("certalert_certificate_validity", holder, AtomicDouble::get)
          .description("Indicates if a certificate is valid (0 = valid, 1 = invalid)")
          .tags("certificate_name", certName, "alias", alias)
          .register(meterRegistry);
      return holder;
    }).set(value);
  }
}
