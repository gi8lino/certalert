package ch.tkb.certalert.web;

import ch.tkb.certalert.model.CertificateInfo;

/**
 * View model for rendering certificate data in the dashboard.
 */
public record CertificateView(
    CertificateInfo.Status status, // Status (VALID, EXPIRED, INVALID)
    String name, // Certificate name
    String type, // Certificate type (PEM, CRT, etc.)
    String alias, // Keystore alias
    String subject, // Subject DN or error message
    String notBeforeFormatted, // Formatted notBefore date
    String expiryDateFormatted, // Formatted expiry date
    String timeRemaining, // Human-readable time remaining
    String statusClass // CSS class for status indicator
) {
  /**
   * Return the name of the fragment to render for the certificate status.
   */
  public String getStatusFragmentName() {
    return "cert-" + status.name().toLowerCase() + "-icon";
  }

}
