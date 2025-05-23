package ch.tkb.certalert.web;

import ch.tkb.certalert.model.CertificateInfo;

/**
 * View model for rendering certificate data in the dashboard.
 */
public record CertificateView(
        String name,                    // Certificate name
        CertificateInfo.Status status,  // Status (VALID, EXPIRED, INVALID)
        String alias,                   // Keystore alias
        String subject,                 // Subject DN or error message
        String notBeforeFormatted,      // Formatted notBefore date
        String expiryDateFormatted,     // Formatted expiry date
        String timeRemaining,           // Human-readable time remaining
        String statusClass              // CSS class for status indicator
) {
    /**
    * Return the name of the fragment to render for the certificate status.
    */
    public String getStatusFragmentName() {
      return "cert-" + status.name().toLowerCase() + "-icon";
    }

}
