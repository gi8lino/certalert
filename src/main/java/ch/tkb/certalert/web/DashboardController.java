package ch.tkb.certalert.web;

import ch.tkb.certalert.collector.CertificateCollector;
import ch.tkb.certalert.config.CertificateConfig;
import ch.tkb.certalert.model.CertificateInfo;
import ch.tkb.certalert.utils.TimeUtils;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Spring MVC Controller for rendering the certificate dashboard. */
@Controller
public class DashboardController {

  /** Placeholder for unknown dates. */
  private static final String NO_DATE_PLACEHOLDER = "—";

  /** Placeholder for missing last update times. */
  private static final String NEVER_PLACEHOLDER = "never";

  /** Placeholder indicating a certificate is expired. */
  private static final String EXPIRED_PLACEHOLDER = "expired";

  /** Service that owns the current certificate snapshot. */
  private final CertificateCollector collector;

  /** Dashboard configuration including expiry thresholds and display format. */
  private final CertificateConfig config;

  /** Version displayed in the dashboard footer. */
  private final String appVersion;

  /** Formatter for displaying dates according to dashboard configuration. */
  private final DateTimeFormatter formatter;

  /** Constructs a DashboardController with the given collector, config, and version. */
  public DashboardController(
      CertificateCollector collector,
      CertificateConfig config,
      @Value("${certalert.version:unknown}") String appVersion) {
    this.collector = collector;
    this.config = config;
    this.appVersion = appVersion;

    // Initialize formatter from dashboard.date-format property
    String pattern = config.dashboard().dateFormat();
    this.formatter = DateTimeFormatter.ofPattern(pattern).withZone(config.dashboard().zoneId());
  }

  /** Handles GET requests to '/' and populates the model for the dashboard view. */
  @GetMapping("/")
  public String dashboard(Model model) {
    Instant currentTime = Instant.now();

    model.addAttribute(
        "lastUpdate", formatInstant(collector.getLastUpdateTime(), NEVER_PLACEHOLDER));
    model.addAttribute("appVersion", appVersion);
    model.addAttribute("checkInterval", TimeUtils.formatDuration(config.checkInterval()));

    List<CertificateView> views =
        collector.getCertificateInfos().stream().map(info -> toView(info, currentTime)).toList();
    model.addAttribute("certificates", views);

    return "dashboard";
  }

  /** Converts a CertificateInfo into a CertificateView for rendering. */
  private CertificateView toView(CertificateInfo info, Instant now) {
    Instant notBefore = info.getNotBefore();
    Instant notAfter = info.getNotAfter();

    String formattedBefore = formatInstant(notBefore, NO_DATE_PLACEHOLDER);
    String formattedAfter = formatInstant(notAfter, NO_DATE_PLACEHOLDER);

    // Format remaining time until expiration
    String remaining = formatRemaining(notAfter, now);

    // Determine CSS class using dashboard thresholds
    String statusClass =
        info.getStatus() == CertificateInfo.Status.VALID
            ? determineStatusClass(notAfter, now)
            : statusClassForNonValidCertificate(info.getStatus());

    return new CertificateView(
        info.getStatus(),
        info.getName(),
        info.getPath(),
        info.getFileName(),
        info.getType(),
        info.getAlias(),
        info.getSubject(),
        formattedBefore,
        formattedAfter,
        remaining,
        statusClass);
  }

  /** Formats an Instant or returns a placeholder if null. */
  private String formatInstant(Instant instant, String placeholder) {
    return instant != null ? formatter.format(instant) : placeholder;
  }

  /** Formats the remaining time until an expiration Instant. */
  private String formatRemaining(Instant end, Instant now) {
    if (end == null) {
      return NO_DATE_PLACEHOLDER;
    }
    if (end.isBefore(now)) {
      return EXPIRED_PLACEHOLDER;
    }
    return TimeUtils.formatPeriod(now, end, false, config.dashboard().zoneId());
  }

  /** Determines a CSS status class based on configured dashboard thresholds. */
  private String determineStatusClass(Instant notAfter, Instant now) {
    if (notAfter == null) {
      return "status-crit";
    }
    Duration remaining = Duration.between(now, notAfter);
    CertificateConfig.Dashboard dashboardConfig = config.dashboard();
    if (remaining.isNegative() || remaining.compareTo(dashboardConfig.criticalThreshold()) <= 0) {
      return "status-crit";
    }
    if (remaining.compareTo(dashboardConfig.warningThreshold()) <= 0) {
      return "status-warn";
    }
    return "status-ok";
  }

  /** Maps non-valid certificate states to their dashboard severity class. */
  private String statusClassForNonValidCertificate(CertificateInfo.Status status) {
    return status == CertificateInfo.Status.EXPIRED ? "status-crit" : "status-error";
  }
}
