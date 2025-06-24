package ch.tkb.certalert.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CertificateLoaderTest {

  private static final String BASE_PATH = "tests/certs";
  private static final String SINGLE_CERT_PATH = BASE_PATH + "/pem/single.pem";
  private static final String BUNDLE_CRT_PATH = BASE_PATH + "/crt/multiple.crt";

  @Test
  @DisplayName("CertificateLoader.load returns first cert from PEM")
  void testLoadFirstCertificate() throws Exception {
    X509Certificate cert = CertificateLoader.load(SINGLE_CERT_PATH);
    assertNotNull(cert);
    assertTrue(cert.getSubjectX500Principal().getName().contains("CN="));
  }

  @Test
  @DisplayName("CertificateLoader.loadAll loads single PEM cert into list")
  void testLoadAllSingleCert() throws Exception {
    List<X509Certificate> certs = CertificateLoader.loadAll(SINGLE_CERT_PATH);
    assertEquals(1, certs.size());
  }

  @Test
  @DisplayName("CertificateLoader.loadAll loads multiple CRT certs from bundle")
  void testLoadAllMultipleCerts() throws Exception {
    List<X509Certificate> certs = CertificateLoader.loadAll(BUNDLE_CRT_PATH);
    assertEquals(2, certs.size());
  }

  @Test
  @DisplayName("CertificateLoader.loadAll throws for missing file")
  void testMissingFile() {
    String path = BASE_PATH + "/crt/missing.crt";
    Exception ex = assertThrows(IllegalArgumentException.class, () -> CertificateLoader.loadAll(path));
    assertTrue(ex.getMessage().contains("does not exist"));
  }

  @Test
  @DisplayName("CertificateLoader.loadAll throws if no certs found")
  void testInvalidPemContent() throws Exception {
    Path emptyFile = Path.of(BASE_PATH, "pem", "empty.pem");
    if (!emptyFile.toFile().exists()) {
      java.nio.file.Files.writeString(emptyFile, ""); // create empty file
    }

    Exception ex = assertThrows(IllegalArgumentException.class, () -> CertificateLoader.loadAll(emptyFile.toString()));
    assertTrue(ex.getMessage().contains("No certificates found"));
  }
}
