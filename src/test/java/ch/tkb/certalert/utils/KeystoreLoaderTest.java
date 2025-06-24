package ch.tkb.certalert.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.junit.jupiter.api.Assertions.*;

class KeystoreLoaderTest {

  @TempDir
  Path tempDir;

  private static final String PASSWORD = "testpass";

  @Test
  @DisplayName("KeystoreLoader.load loads valid JKS keystore using lowercase alias")
  void testLoadValidJksWithAlias() throws Exception {
    Path file = tempDir.resolve("test.jks");

    KeyStore ks = KeyStore.getInstance("JKS");
    ks.load(null, null);
    try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
      ks.store(fos, PASSWORD.toCharArray());
    }

    KeyStore loaded = KeystoreLoader.load("jks", file.toString(), PASSWORD); // lowercase alias
    assertNotNull(loaded);
    assertEquals("JKS", loaded.getType());
  }

  @Test
  @DisplayName("KeystoreLoader.load supports p12 alias for PKCS12")
  void testLoadP12Alias() throws Exception {
    Path file = tempDir.resolve("test.p12");

    KeyStore ks = KeyStore.getInstance("PKCS12");
    ks.load(null, null);
    try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
      ks.store(fos, PASSWORD.toCharArray());
    }

    KeyStore loaded = KeystoreLoader.load("p12", file.toString(), PASSWORD);
    assertNotNull(loaded);
    assertEquals("PKCS12", loaded.getType());
  }

  @Test
  @DisplayName("KeystoreLoader.load throws if file doesn't exist")
  void testFileNotFound() {
    String nonexistent = tempDir.resolve("missing.jks").toString();
    Exception ex = assertThrows(Exception.class, () -> KeystoreLoader.load("jks", nonexistent, PASSWORD));
    assertTrue(ex instanceof java.io.FileNotFoundException);
  }

  @Test
  @DisplayName("KeystoreLoader.load throws on unsupported keystore type")
  void testUnsupportedType() throws Exception {
    Path file = tempDir.resolve("dummy.ks");
    Files.writeString(file, "not-a-keystore");

    Exception ex = assertThrows(IllegalArgumentException.class,
        () -> KeystoreLoader.load("fake", file.toString(), "irrelevant"));
    assertTrue(ex.getMessage().contains("Unsupported keystore type"));
  }

  @Test
  @DisplayName("KeystoreLoader.load throws on wrong password")
  void testWrongPassword() throws Exception {
    Path file = tempDir.resolve("secure.jks");

    KeyStore ks = KeyStore.getInstance("JKS");
    ks.load(null, null);
    try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
      ks.store(fos, PASSWORD.toCharArray());
    }

    Exception ex = assertThrows(Exception.class, () -> KeystoreLoader.load("jks", file.toString(), "wrongpass"));
    assertTrue(ex.getMessage().toLowerCase().contains("password") || ex instanceof java.io.IOException);
  }

  @Test
  @DisplayName("KeystoreLoader.load supports empty string password for unprotected keystore")
  void testEmptyPasswordForUnprotectedKeystore() throws Exception {
    Path file = tempDir.resolve("emptypass.jks");

    KeyStore ks = KeyStore.getInstance("JKS");
    ks.load(null, null);
    try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
      ks.store(fos, "".toCharArray());
    }

    KeyStore loaded = KeystoreLoader.load("jks", file.toString(), "");
    assertNotNull(loaded);
  }
}
