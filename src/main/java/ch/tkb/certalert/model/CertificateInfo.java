package ch.tkb.certalert.model;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/** Holds certificate metadata and its current validation status. */
@Value
@Builder
public class CertificateInfo {
  String name;
  String path;
  String fileName;
  String type;
  String alias;
  String subject;
  Instant notBefore;
  Instant notAfter;
  Status status;

  public enum Status {
    VALID,
    EXPIRED,
    INVALID
  }
}
