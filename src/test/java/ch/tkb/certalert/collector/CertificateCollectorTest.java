package ch.tkb.certalert.collector;

import static org.assertj.core.api.Assertions.assertThat;

import ch.tkb.certalert.model.CertificateInfo.Status;
import ch.tkb.certalert.utils.CertificateLoader;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CertificateCollectorTest {

  @Test
  void marksCertificatesOutsideTheirValidityWindowAsInvalidOrExpired() {
    Instant notBefore = Instant.parse("2030-01-01T00:00:00Z");
    Instant notAfter = Instant.parse("2030-12-31T23:59:59Z");

    assertThat(CertificateCollector.determineStatus(notBefore.minusSeconds(1), notBefore, notAfter))
        .isEqualTo(Status.NOT_YET_VALID);
    assertThat(CertificateCollector.determineStatus(notBefore, notBefore, notAfter))
        .isEqualTo(Status.VALID);
    assertThat(CertificateCollector.determineStatus(notAfter, notBefore, notAfter))
        .isEqualTo(Status.EXPIRED);
  }

  @Test
  void usesStableFingerprintsForCertificatesInPemBundles() throws Exception {
    var certificates = CertificateLoader.loadAll("tests/certs/crt/multiple.crt");

    String firstAlias = CertificateCollector.pemAlias(certificates.size(), certificates.getFirst());
    String secondAlias = CertificateCollector.pemAlias(certificates.size(), certificates.get(1));

    assertThat(firstAlias).matches("sha256:[0-9a-f]{64}");
    assertThat(secondAlias).matches("sha256:[0-9a-f]{64}");
    assertThat(firstAlias).isNotEqualTo(secondAlias);
  }
}
