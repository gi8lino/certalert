package ch.tkb.certalert.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CertificateConfigTest {

  @Test
  void rejectsAConfigurationWithInvalidDurations() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CertificateConfig(Duration.ZERO, List.of(), null))
        .withMessage("checkInterval must be positive");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new CertificateConfig.Dashboard(Duration.ofDays(3), Duration.ofDays(4), null))
        .withMessage("criticalThreshold must not be greater than warningThreshold");
  }

  @Test
  void copiesTheConfiguredCertificateList() {
    List<CertificateConfig.CertificateEntry> configuredEntries = new ArrayList<>();
    CertificateConfig config =
        new CertificateConfig(Duration.ofMinutes(5), configuredEntries, null);

    configuredEntries.add(
        new CertificateConfig.CertificateEntry("gateway", "/gateway.pem", "pem", null));

    assertThat(config.certificates()).isEmpty();
  }
}
