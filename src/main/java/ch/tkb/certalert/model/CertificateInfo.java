package ch.tkb.certalert.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Holds certificate metadata and its current validation status,
 * with a mutable 'seen' flag for pruning.
 */
@Data
@Builder
public class CertificateInfo {
  private final String name;
  private final String type;
  private final String alias;
  private final String subject;
  private final Instant notBefore;
  private final Instant notAfter;
  private final Status status;
  /**
   * Tracks whether this info was seen in the latest poll cycle.
   */
  @Builder.Default
  private boolean seen = true;

  public enum Status {
    VALID, EXPIRED, INVALID
  }
}
