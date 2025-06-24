package ch.tkb.certalert.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CertificateLoaderTest {

  private static final String SINGLE_CERT_PATH = "/tmp/certloader_tests/single.pem";
  private static final String BUNDLE_CERT_PATH = "/tmp/certloader_tests/bundle.pem";

  @Test
  @DisplayName("CertificateLoader.load returns first cert from PEM")
  void testLoadFirstCertificate() throws Exception {
    X509Certificate cert = CertificateLoader.load(SINGLE_CERT_PATH);
    assertNotNull(cert);
    assertEquals("CN=test.com, OU=DevDept, O=Test, L=Graz5s, ST=Nideroer, C=AT",
        cert.getSubjectX500Principal().getName());
  }

  @Test
  @DisplayName("CertificateLoader.loadAll loads single PEM cert into list")
  void testLoadAllSingleCert() throws Exception {
    List<X509Certificate> certs = CertificateLoader.loadAll(SINGLE_CERT_PATH);
    assertEquals(1, certs.size());
  }

  @Test
  @DisplayName("CertificateLoader.loadAll loads multiple PEM certs from bundle")
  void testLoadAllMultipleCerts() throws Exception {
    List<X509Certificate> certs = CertificateLoader.loadAll(BUNDLE_CERT_PATH);
    assertEquals(2, certs.size());
    assertEquals(certs.get(0).getSubjectX500Principal(), certs.get(1).getSubjectX500Principal());
  }

  @Test
  @DisplayName("CertificateLoader.loadAll throws for missing file")
  void testMissingFile() {
    String path = "/tmp/certloader_tests/missing.pem";
    Exception ex = assertThrows(IllegalArgumentException.class, () -> CertificateLoader.loadAll(path));
    assertTrue(ex.getMessage().contains("does not exist"));
  }

  @Test
  @DisplayName("CertificateLoader.loadAll throws if no certs found")
  void testInvalidPemContent() throws Exception {
    Path emptyFile = Path.of("/tmp/certloader_tests/empty.pem");
    if (!emptyFile.toFile().exists()) {
      java.nio.file.Files.writeString(emptyFile, ""); // create empty file
    }

    Exception ex = assertThrows(IllegalArgumentException.class, () -> CertificateLoader.loadAll(emptyFile.toString()));
    assertTrue(ex.getMessage().contains("No certificates found"));
  }
}
