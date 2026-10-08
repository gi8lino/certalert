package ch.tkb.certalert.utils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for formatting durations between two points in time into human-readable strings.
 *
 * <p>Supports both short (e.g. "1y, 2mo, 5d") and long formats (e.g. "1 year, 2 months, 5 days"),
 * with comma-separated units.
 */
public final class TimeUtils {

  /** Prevents instantiation of this static utility class. */
  private TimeUtils() {}

  /**
   * Formats the difference between two Instants in short form. Returns null if either Instant is
   * null or end is before start.
   */
  public static String formatPeriod(Instant start, Instant end) {
    if (start == null || end == null || end.isBefore(start)) {
      return null;
    }
    return formatPeriod(start, end, false);
  }

  /**
   * Formats the difference between two Instants.
   *
   * @param start the start Instant (may be null)
   * @param end the end Instant (may be null)
   * @param longFormat true for full unit names, false for abbreviated
   * @return formatted string or null if end is before start or any Instant is null
   */
  public static String formatPeriod(Instant start, Instant end, boolean longFormat) {
    if (start == null || end == null || end.isBefore(start)) {
      return null;
    }

    LocalDateTime from = LocalDateTime.ofInstant(start, ZoneId.systemDefault());
    LocalDateTime to = LocalDateTime.ofInstant(end, ZoneId.systemDefault());

    Period datePart = Period.between(from.toLocalDate(), to.toLocalDate());
    LocalDateTime afterDate = from.plus(datePart);

    Duration timePart = Duration.between(afterDate, to);

    // Build and collect formatted parts for each time unit (years, months, days,
    // hours, minutes)
    List<String> parts = new ArrayList<>();
    for (Unit u : Unit.values()) {
      // Determine quantity of the current unit from datePart or timePart
      long qty;
      switch (u) {
        case YEARS:
          qty = datePart.getYears();
          break;
        case MONTHS:
          qty = datePart.getMonths();
          break;
        case DAYS:
          qty = datePart.getDays();
          break;
        case HOURS:
          qty = timePart.toHoursPart();
          break;
        case MINUTES:
          qty = timePart.toMinutesPart();
          break;
        default:
          qty = 0;
      }

      // Include this unit if non-zero, or always include minutes if no other parts
      // yet
      if (qty > 0 || (u == Unit.MINUTES && parts.isEmpty())) {
        String label = longFormat ? (qty == 1 ? u.singular : u.plural) : u.shortLabel;
        parts.add(qty + (longFormat ? label : u.shortLabel));
      }
    }

    // Join all unit parts with commas into final string
    return String.join(", ", parts);
  }

  /**
   * Formats a Duration from now to now + duration in short form. Returns null if duration is null.
   */
  public static String formatDuration(Duration duration) {
    return formatDuration(duration, false);
  }

  /**
   * Formats a Duration from now to now + duration.
   *
   * @param duration the Duration to format (may be null)
   * @param longFormat true for full unit names, false for abbreviated units
   * @return formatted duration string or null if duration is null
   */
  public static String formatDuration(Duration duration, boolean longFormat) {
    if (duration == null) {
      return null;
    }
    Instant now = Instant.now();
    return formatPeriod(now, now.plus(duration), longFormat);
  }

  /** Defines display labels for the date and time units included in a formatted period. */
  private enum Unit {
    /** Calendar year unit. */
    YEARS("y", " year", " years"),
    /** Calendar month unit. */
    MONTHS("mo", " month", " months"),
    /** Calendar day unit. */
    DAYS("d", " day", " days"),
    /** Elapsed hour unit. */
    HOURS("h", " hour", " hours"),
    /** Elapsed minute unit. */
    MINUTES("m", " minute", " minutes");

    /** Abbreviated label for the unit. */
    final String shortLabel;

    /** Singular long-form label for the unit. */
    final String singular;

    /** Plural long-form label for the unit. */
    final String plural;

    /** Creates a display definition for one time unit. */
    Unit(String shortLabel, String singular, String plural) {
      this.shortLabel = shortLabel;
      this.singular = singular;
      this.plural = plural;
    }
  }
}
