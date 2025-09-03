package ch.tkb.certalert.web;

import ch.tkb.certalert.collector.CertificateCollector;
import ch.tkb.certalert.config.CertificateConfig;
import ch.tkb.certalert.model.CertificateInfo;
import ch.tkb.certalert.utils.TimeUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Spring MVC Controller for rendering the certificate dashboard.
 */
@Controller
public class DashboardController {

  /**
   * Placeholder for unknown dates.
   */
  private static final String NO_DATE_PLACEHOLDER = "—";

  /**
   * Placeholder for missing last update times.
   */
  private static final String NEVER_PLACEHOLDER = "never";

  /**
   * Placeholder indicating a certificate is expired.
   */
  private static final String EXPIRED_PLACEHOLDER = "expired";

  private final CertificateCollector collector;
  private final CertificateConfig config;
  private final String appVersion;

  /**
   * Formatter for displaying dates according to dashboard configuration.
   */
  private final DateTimeFormatter formatter;

  /**
   * Constructs a DashboardController with the given collector, config, and
   * version.
   */
  public DashboardController(
      CertificateCollector collector,
      CertificateConfig config,
      @Value("${certalert.version:unknown}") String appVersion) {
    this.collector = collector;
    this.config = config;
    this.appVersion = appVersion;

    // Initialize formatter from dashboard.date-format property
    String pattern = config.dashboard().dateFormat();
    this.formatter = DateTimeFormatter.ofPattern(pattern)
        .withZone(ZoneId.systemDefault());
  }

  /**
   * Handles GET requests to '/' and populates the model for the dashboard view.
   */
  @GetMapping("/")
  public String dashboard(Model model) {
    Instant now = Instant.now();

    model.addAttribute("lastUpdate", formatInstant(collector.getLastUpdateTime(), NEVER_PLACEHOLDER));
    model.addAttribute("appVersion", appVersion);
    model.addAttribute("checkInterval", TimeUtils.formatDuration(config.checkInterval()));

    List<CertificateView> views = collector.getCertificateInfos().stream()
        .map(info -> toView(info, now))
        .toList();
    model.addAttribute("certificates", views);

    return "dashboard";
  }

  /**
   * Converts a CertificateInfo into a CertificateView for rendering.
   */
  private CertificateView toView(CertificateInfo info, Instant now) {
    Instant notBefore = info.getNotBefore();
    Instant notAfter = info.getNotAfter();

    String formattedBefore = formatInstant(notBefore, NO_DATE_PLACEHOLDER);
    String formattedAfter = formatInstant(notAfter, NO_DATE_PLACEHOLDER);

    // Format remaining time until expiration
    String remaining = formatRemaining(notAfter, now);

    // Determine CSS class using dashboard thresholds
    String statusClass = info.getStatus() == CertificateInfo.Status.INVALID
        ? "status-error"
        : determineStatusClass(notAfter, now);

    return new CertificateView(
        info.getStatus(), info.getName(), info.getPath(), info.getFileName(), info.getType(), info.getAlias(),
        info.getSubject(),
        formattedBefore, formattedAfter,
        remaining, statusClass);
  }

  /**
   * Formats an Instant or returns a placeholder if null.
   */
  private String formatInstant(Instant ts, String placeholder) {
    return ts != null ? formatter.format(ts) : placeholder;
  }

  /**
   * Formats the remaining time until an expiration Instant.
   */
  private String formatRemaining(Instant end, Instant now) {
    if (end == null) {
      return NO_DATE_PLACEHOLDER;
    }
    if (end.isBefore(now)) {
      return EXPIRED_PLACEHOLDER;
    }
    return TimeUtils.formatPeriod(now, end);
  }

  /**
   * Determines a CSS status class based on configured dashboard thresholds.
   */
  private String determineStatusClass(Instant notAfter, Instant now) {
    if (notAfter == null) {
      return "status-crit";
    }
    Duration remaining = Duration.between(now, notAfter);
    CertificateConfig.Dashboard dash = config.dashboard();
    if (remaining.isNegative() || remaining.compareTo(dash.criticalThreshold()) <= 0) {
      return "status-crit";
    }
    if (remaining.compareTo(dash.warningThreshold()) <= 0) {
      return "status-warn";
    }
    return "status-ok";
  }
}
