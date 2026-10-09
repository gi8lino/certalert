package ch.tkb.certalert.web;

import ch.tkb.certalert.model.CertificateInfo;

/** View model for rendering certificate data in the dashboard. */
public record CertificateView(
    CertificateInfo.Status status, // Collection state for the certificate
    String name, // Certificate name
    String path, // Path to certificate file
    String fileName, // File name
    String type, // Certificate type (PEM, CRT, etc.)
    String alias, // Keystore alias
    String subject, // Subject DN or error message
    String notBeforeFormatted, // Formatted notBefore date
    String expiryDateFormatted, // Formatted expiry date
    String timeRemaining, // Human-readable time remaining
    String statusClass // CSS class for status indicator
    ) {
  /** Return the name of the fragment to render for the certificate status. */
  public String getStatusFragmentName() {
    return switch (status) {
      case VALID -> "cert-valid-icon";
      case EXPIRED -> "cert-expired-icon";
      default -> "cert-invalid-icon";
    };
  }
}
