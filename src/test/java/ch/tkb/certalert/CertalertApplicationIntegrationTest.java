package ch.tkb.certalert;

import static org.assertj.core.api.Assertions.assertThat;

import ch.tkb.certalert.collector.CertificateCollector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "certalert.check-interval=1h",
      "certalert.certificates[0].name=integration-certificate",
      "certalert.certificates[0].path=tests/certs/pem/single.pem",
      "certalert.certificates[0].type=pem"
    })
class CertalertApplicationIntegrationTest {

  @Autowired private CertificateCollector collector;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  @BeforeEach
  void collectCertificates() {
    collector.collectCertificateData();
  }

  @Test
  void exposesHealthCertificateDataAndMetrics() {
    HttpResponse<String> health = get("/health");
    HttpResponse<String> certificates = get("/api/certificates");
    HttpResponse<String> metrics = get("/metrics");

    assertThat(health.statusCode()).isEqualTo(200);
    assertThat(health.body()).contains("UP");
    assertThat(certificates.statusCode()).isEqualTo(200);
    assertThat(certificates.body()).contains("integration-certificate");
    assertThat(metrics.statusCode()).isEqualTo(200);
    assertThat(metrics.body())
        .contains("certalert_certificate_validity")
        .contains("certificate_name=\"integration-certificate\"");
  }

  private HttpResponse<String> get(String path) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
      return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (Exception exception) {
      throw new AssertionError("Failed to call " + path, exception);
    }
  }
}
