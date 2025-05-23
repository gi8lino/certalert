package ch.tkb.certalert.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import java.nio.file.Path;
import java.nio.file.Paths;

import java.security.KeyStore;

/**
 * Utility for loading keystore files into KeyStore instances.
 */
public class KeystoreLoader {

  /**
   * Loads a keystore of the specified type from the given path.
   */
  public static KeyStore load(String type, String path, String password) throws Exception {
    Path normalized = Paths.get(path)
        .toAbsolutePath()
        .normalize();
    File file = normalized.toFile();

    if (!file.exists() || !file.isFile()) {
      throw new FileNotFoundException("Keystore file does not exist: " + normalized);
    }

    KeyStore keyStore;
    try {
      keyStore = KeyStore.getInstance(type);
    } catch (Exception e) {
      throw new IllegalArgumentException("Unsupported keystore type: " + type, e);
    }

    try (FileInputStream fis = new FileInputStream(file)) {
      keyStore.load(fis, password != null ? password.toCharArray() : null);
    }

    return keyStore;
  }
}
