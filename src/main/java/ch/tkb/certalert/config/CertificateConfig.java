package ch.tkb.certalert.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Configuration properties mapped from 'certalert' prefix in YAML/properties.
 */
@Validated
@ConfigurationProperties(prefix = "certalert")
public record CertificateConfig(
    Duration checkInterval, // Interval between checks (e.g., PT2M)
    @Valid List<CertificateEntry> certificates, // List of configured certificates
    Dashboard dashboard // Dashboard-specific settings
) {

  /**
   * Initializes defaults for checkInterval, dashboard, and certificates if null.
   */
  public CertificateConfig {
    checkInterval = checkInterval != null ? checkInterval : Duration.ofMinutes(10);
    dashboard = dashboard != null ? dashboard : new Dashboard(null, null, null);
    certificates = certificates != null ? certificates : List.of();
  }

  /**
   * Dashboard settings including thresholds and date format.
   */
  public record Dashboard(
      Duration warningThreshold, // Warn if certificate expires within this duration
      Duration criticalThreshold, // Critical if certificate expires within this duration
      String dateFormat // Date/time format pattern for display
  ) {

    // Default values for dashboard settings
    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssXXX";
    public static final Duration DEFAULT_WARNING_THRESHOLD = Duration.ofDays(20);
    public static final Duration DEFAULT_CRITICAL_THRESHOLD = Duration.ofDays(3);
    private static final Logger log = LoggerFactory.getLogger(Dashboard.class);

    /**
     * Initializes defaults and validates the dateFormat pattern.
     */
    public Dashboard {
      warningThreshold = warningThreshold != null ? warningThreshold : DEFAULT_WARNING_THRESHOLD;
      criticalThreshold = criticalThreshold != null ? criticalThreshold : DEFAULT_CRITICAL_THRESHOLD;

      if (!isValidDateFormat(dateFormat)) {
        if (dateFormat != null) {
          log.warn("Invalid dateFormat '{}', using default.", dateFormat);
        }
        dateFormat = DEFAULT_DATE_FORMAT;
      }
    }

    /**
     * Validates whether the provided date format pattern is valid.
     */
    private static boolean isValidDateFormat(String format) {
      if (format == null) {
        return false;
      }
      try {
        DateTimeFormatter.ofPattern(format);
        return true;
      } catch (IllegalArgumentException e) {
        return false;
      }
    }

  }

  /**
   * Describes a certificate entry with metadata and optional password.
   */
  public record CertificateEntry(
      @NotEmpty String name, // Logical name of the certificate
      @NotEmpty String path, // Path to the certificate file
      @NotEmpty String type, // Type (e.g., JKS, PKCS12)
      String password // Optional password (may be null)
  ) {
  }
}
