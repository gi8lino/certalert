package ch.tkb.certalert.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import ch.tkb.certalert.model.CertificateInfo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CertificateMetricsPublisherTest {

  @Test
  void replacesMetricValuesAndRemovesMetricsForCertificatesMissingFromTheSnapshot() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    CertificateMetricsPublisher publisher = new CertificateMetricsPublisher(registry);
    CertificateInfo original = certificate(Instant.ofEpochSecond(1_000));
    CertificateInfo updated = certificate(Instant.ofEpochSecond(2_000));

    publisher.publish(List.of(original));
    publisher.publish(List.of(updated));

    assertThat(expirationGauge(registry).value()).isEqualTo(2_000);
    assertThat(daysRemainingGauge(registry).value()).isNotEqualTo(0);

    publisher.publish(List.of());

    assertThat(registry.find("certalert_certificate_expiration_seconds").gauge()).isNull();
    assertThat(registry.find("certalert_certificate_days_remaining").gauge()).isNull();
    assertThat(registry.find("certalert_certificate_validity").gauge()).isNull();
  }

  private CertificateInfo certificate(Instant notAfter) {
    return CertificateInfo.builder()
        .name("gateway")
        .path("/etc/certificates/gateway.pem")
        .type("pem")
        .alias("default")
        .notAfter(notAfter)
        .status(CertificateInfo.Status.VALID)
        .build();
  }

  private io.micrometer.core.instrument.Gauge expirationGauge(SimpleMeterRegistry registry) {
    return registry.find("certalert_certificate_expiration_seconds").gauge();
  }

  private io.micrometer.core.instrument.Gauge daysRemainingGauge(SimpleMeterRegistry registry) {
    return registry.find("certalert_certificate_days_remaining").gauge();
  }
}
