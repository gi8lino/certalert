package ch.tkb.certalert.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.security.KeyStore;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class KeystoreLoaderTest {

  @TempDir
  Path tempDir;

  private static final String PASSWORD = "testpass";

  @Test
  @DisplayName("KeystoreLoader.load loads valid JKS keystore")
  void testLoadValidJKS() throws Exception {
    Path file = tempDir.resolve("test.jks");

    // Create and store a simple keystore
    KeyStore ks = KeyStore.getInstance("JKS");
    ks.load(null, null); // initialize empty
    try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
      ks.store(fos, PASSWORD.toCharArray());
    }

    // Load it back
    KeyStore loaded = KeystoreLoader.load("JKS", file.toString(), PASSWORD);
    assertNotNull(loaded);
    assertEquals("JKS", loaded.getType());
  }

  @Test
  @DisplayName("KeystoreLoader.load throws if file doesn't exist")
  void testFileNotFound() {
    String nonexistent = tempDir.resolve("missing.jks").toString();
    Exception ex = assertThrows(Exception.class, () -> KeystoreLoader.load("JKS", nonexistent, PASSWORD));
    assertTrue(ex instanceof java.io.FileNotFoundException);
  }

  @Test
  @DisplayName("KeystoreLoader.load throws on unsupported keystore type")
  void testUnsupportedType() throws Exception {
    Path file = tempDir.resolve("dummy.ks");
    Files.writeString(file, "not-a-keystore");

    Exception ex = assertThrows(IllegalArgumentException.class,
        () -> KeystoreLoader.load("FAKE", file.toString(), "irrelevant"));
    assertTrue(ex.getMessage().contains("Unsupported keystore type"));
  }

  @Test
  @DisplayName("KeystoreLoader.load throws on wrong password")
  void testWrongPassword() throws Exception {
    Path file = tempDir.resolve("secure.jks");

    // Create keystore with password
    KeyStore ks = KeyStore.getInstance("JKS");
    ks.load(null, null);
    try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
      ks.store(fos, PASSWORD.toCharArray());
    }

    Exception ex = assertThrows(Exception.class, () -> KeystoreLoader.load("JKS", file.toString(), "wrongpass"));
    assertTrue(ex.getMessage().contains("password") || ex instanceof java.io.IOException);
  }

  @Test
  @DisplayName("KeystoreLoader.load supports empty string password for unprotected keystore")
  void testEmptyPasswordForUnprotectedKeystore() throws Exception {
    Path file = tempDir.resolve("emptypass.jks");

    // Create keystore with empty password
    KeyStore ks = KeyStore.getInstance("JKS");
    ks.load(null, null);
    try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
      ks.store(fos, "".toCharArray());
    }

    KeyStore loaded = KeystoreLoader.load("JKS", file.toString(), "");
    assertNotNull(loaded);
  }

}
