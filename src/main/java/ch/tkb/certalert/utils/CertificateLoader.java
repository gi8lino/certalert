package ch.tkb.certalert.utils;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Utility class for loading X.509 certificates from PEM or CRT files.
 * <p>
 * Supports both single certificates and PEM bundles containing multiple
 * certificates (e.g., full certificate chains).
 */
public class CertificateLoader {

  /**
   * Loads all X.509 certificates from the given file path.
   *
   * @param path the absolute or relative path to a PEM or CRT file
   * @return a list of {@link X509Certificate} objects; never null
   * @throws Exception if the file is missing, unreadable, or contains no valid
   *                   X.509 certificates
   */
  public static List<X509Certificate> loadAll(String path) throws Exception {
    Path normalized = Paths.get(path).toAbsolutePath().normalize();

    if (!Files.exists(normalized) || !Files.isRegularFile(normalized)) {
      throw new IllegalArgumentException("Certificate file does not exist: " + normalized);
    }

    try (InputStream in = new FileInputStream(normalized.toFile())) {
      CertificateFactory factory = CertificateFactory.getInstance("X.509");
      Collection<? extends Certificate> certs = factory.generateCertificates(in);

      List<X509Certificate> result = new ArrayList<>();
      for (Certificate cert : certs) {
        result.add((X509Certificate) cert);
      }

      if (result.isEmpty()) {
        throw new IllegalArgumentException("No certificates found in file: " + normalized);
      }

      return result;
    }
  }

  /**
   * Loads the first X.509 certificate from the file.
   * Intended for single-certificate PEM or CRT files.
   *
   * @param path the file path to load
   * @return the first {@link X509Certificate} found in the file
   * @throws Exception if the file is invalid or contains no valid certificate
   */
  public static X509Certificate load(String path) throws Exception {
    List<X509Certificate> all = loadAll(path);
    return all.get(0);
  }
}
