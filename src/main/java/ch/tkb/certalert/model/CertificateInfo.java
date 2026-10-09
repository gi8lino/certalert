package ch.tkb.certalert.model;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/** Holds certificate metadata and its current validation status. */
@Value
@Builder
public class CertificateInfo {
  /** Configured display name for the certificate source. */
  String name;

  /** Source path of the certificate or keystore. */
  String path;

  /** File name extracted from the certificate source path. */
  String fileName;

  /** Configured certificate or keystore type. */
  String type;

  /** Alias within a keystore or generated alias within a certificate bundle. */
  String alias;

  /** X.509 subject distinguished name or the collection error message. */
  String subject;

  /** Start of the certificate validity period. */
  Instant notBefore;

  /** End of the certificate validity period. */
  Instant notAfter;

  /** Result of validating and loading the certificate. */
  Status status;

  /** Represents the validity state of a collected certificate. */
  public enum Status {
    /** Certificate is currently valid. */
    VALID,
    /** Certificate has passed its expiry date. */
    EXPIRED,
    /** Certificate validity starts in the future. */
    NOT_YET_VALID,
    /** The configured alias does not contain a certificate. */
    MISSING_CERTIFICATE,
    /** Certificate data or its containing keystore could not be loaded. */
    LOAD_FAILED
  }
}
