package ch.tkb.certalert.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configuration properties mapped from 'certalert' prefix in YAML/properties. */
@Validated
@ConfigurationProperties(prefix = "certalert")
public record CertificateConfig(
    @Positive Duration checkInterval, // Interval between checks (e.g., PT2M)
    List<@Valid CertificateEntry> certificates, // List of configured certificates
    Dashboard dashboard // Dashboard-specific settings
    ) {

  /** Initializes defaults for checkInterval, dashboard, and certificates if null. */
  public CertificateConfig {
    checkInterval = checkInterval != null ? checkInterval : Duration.ofMinutes(10);
    dashboard = dashboard != null ? dashboard : new Dashboard(null, null, null, null);
    certificates = certificates != null ? List.copyOf(certificates) : List.of();
    if (checkInterval.isZero() || checkInterval.isNegative()) {
      throw new IllegalArgumentException("checkInterval must be positive");
    }
  }

  /** Dashboard settings including thresholds and date format. */
  public record Dashboard(
      @Positive Duration warningThreshold, // Warn if certificate expires within this duration
      @Positive Duration criticalThreshold, // Critical if certificate expires within this duration
      String dateFormat, // Date/time format pattern for display
      ZoneId zoneId // Time zone used for dates, logs, and dashboard durations
      ) {

    // Default values for dashboard settings
    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssXXX";
    public static final Duration DEFAULT_WARNING_THRESHOLD = Duration.ofDays(20);
    public static final Duration DEFAULT_CRITICAL_THRESHOLD = Duration.ofDays(3);
    public static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("UTC");
    private static final Logger log = LoggerFactory.getLogger(Dashboard.class);

    /** Initializes defaults and validates the dateFormat pattern. */
    public Dashboard {
      warningThreshold = warningThreshold != null ? warningThreshold : DEFAULT_WARNING_THRESHOLD;
      criticalThreshold =
          criticalThreshold != null ? criticalThreshold : DEFAULT_CRITICAL_THRESHOLD;
      zoneId = zoneId != null ? zoneId : DEFAULT_ZONE_ID;
      validateThresholds(warningThreshold, criticalThreshold);

      if (!isValidDateFormat(dateFormat)) {
        if (dateFormat != null) {
          log.warn("Invalid dateFormat '{}', using default.", dateFormat);
        }
        dateFormat = DEFAULT_DATE_FORMAT;
      }
    }

    /**
     * Ensures both thresholds are positive and the critical threshold is no greater than warning.
     */
    private static void validateThresholds(Duration warningThreshold, Duration criticalThreshold) {
      if (warningThreshold.isZero() || warningThreshold.isNegative()) {
        throw new IllegalArgumentException("warningThreshold must be positive");
      }
      if (criticalThreshold.isZero() || criticalThreshold.isNegative()) {
        throw new IllegalArgumentException("criticalThreshold must be positive");
      }
      if (criticalThreshold.compareTo(warningThreshold) > 0) {
        throw new IllegalArgumentException(
            "criticalThreshold must not be greater than warningThreshold");
      }
    }

    /** Validates whether the provided date format pattern is valid. */
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

  /** Describes a certificate entry with metadata and optional password. */
  public record CertificateEntry(
      @NotBlank String name, // Logical name of the certificate
      @NotBlank String path, // Path to the certificate file
      @NotBlank String type, // Type (e.g., JKS, PKCS12)
      String password // Optional password (may be null)
      ) {}
}
