package ch.tkb.certalert.model;

/** Stable identity for one configured certificate or keystore alias. */
public record CertificateIdentity(
    String path, // Source path of the certificate or keystore.
    String type, // Configured certificate or keystore type.
    String name, // Configured display name.
    String alias // Alias within the keystore or certificate bundle.
    ) {
  /** Creates the stable metric identity for a collected certificate. */
  public static CertificateIdentity from(CertificateInfo certInfo) {
    return new CertificateIdentity(
        certInfo.getPath(), certInfo.getType(), certInfo.getName(), certInfo.getAlias());
  }
}
