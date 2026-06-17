package ch.tkb.certalert.model;

/** Stable identity for one configured certificate or keystore alias. */
public record CertificateIdentity(String path, String type, String name, String alias) {
  public static CertificateIdentity from(CertificateInfo certInfo) {
    return new CertificateIdentity(
        certInfo.getPath(), certInfo.getType(), certInfo.getName(), certInfo.getAlias());
  }
}
