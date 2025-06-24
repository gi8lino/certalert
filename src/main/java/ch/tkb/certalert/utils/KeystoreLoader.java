package ch.tkb.certalert.utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.Map;

/**
 * Utility for loading keystore files (e.g., JKS, PKCS12) into {@link KeyStore}
 * instances.
 * <p>
 * Supports formats such as {@code jks}, {@code pkcs12}, {@code p12},
 * {@code jceks}, etc.
 * Input types are case-insensitive and some common aliases are automatically
 * mapped.
 */
public class KeystoreLoader {

  /**
   * Maps common lowercase or alias keystore type names to their canonical names
   * used by {@link KeyStore#getInstance(String)}.
   */
  private static final Map<String, String> TYPE_ALIASES = Map.of(
      "jks", "JKS",
      "jceks", "JCEKS",
      "pkcs12", "PKCS12",
      "p12", "PKCS12",
      "dks", "DKS",
      "pkcs11", "PKCS11");

  /**
   * Loads a keystore of the specified type from the given file path using the
   * provided password.
   *
   * @param type     the type of the keystore (e.g. {@code "JKS"},
   *                 {@code "PKCS12"}, {@code "JCEKS"}). Case-insensitive.
   * @param path     the path to the keystore file; relative paths will be
   *                 resolved to absolute
   * @param password the password for the keystore; may be {@code null} for
   *                 keystores without a password
   * @return a loaded {@link KeyStore} instance
   * @throws FileNotFoundException    if the file does not exist or is not a
   *                                  regular file
   * @throws IllegalArgumentException if the specified keystore type is not
   *                                  supported
   * @throws Exception                if loading the keystore fails due to IO or
   *                                  format issues
   */
  public static KeyStore load(String type, String path, String password) throws Exception {
    Path normalized = Paths.get(path).toAbsolutePath().normalize();

    if (!Files.exists(normalized) || !Files.isRegularFile(normalized)) {
      throw new FileNotFoundException("Keystore file does not exist: " + normalized);
    }

    String normalizedType = TYPE_ALIASES.get(type.toLowerCase());
    if (normalizedType == null) {
      throw new IllegalArgumentException("Unsupported keystore type: " + type);
    }

    KeyStore keyStore = KeyStore.getInstance(normalizedType);
    try (FileInputStream fis = new FileInputStream(normalized.toFile())) {
      keyStore.load(fis, password != null ? password.toCharArray() : null);
    }

    return keyStore;
  }
}
