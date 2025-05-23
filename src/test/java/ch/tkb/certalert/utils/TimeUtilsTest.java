package ch.tkb.certalert.utils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TimeUtils}.
 * <p>
 * Tests are run with UTC default timezone for consistent results.
 */
class TimeUtilsTest {

  private static ZoneId originalZone;

  @BeforeAll
  static void setUp() {
    // Capture and override system timezone to UTC for deterministic behavior
    originalZone = ZoneId.systemDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }

  @AfterAll
  static void tearDown() {
    // Restore original timezone after tests
    TimeZone.setDefault(TimeZone.getTimeZone(originalZone));
  }

  @Test
  @DisplayName("formatPeriod returns null if start is null")
  void formatPeriod_nullStart() {
    Instant now = Instant.parse("2025-01-01T00:00:00Z");
    assertNull(TimeUtils.formatPeriod(null, now));
  }

  @Test
  @DisplayName("formatPeriod returns null if end is null")
  void formatPeriod_nullEnd() {
    Instant now = Instant.parse("2025-01-01T00:00:00Z");
    assertNull(TimeUtils.formatPeriod(now, null));
  }

  @Test
  @DisplayName("formatPeriod returns null if end is before start")
  void formatPeriod_endBeforeStart() {
    Instant start = Instant.parse("2025-01-02T00:00:00Z");
    Instant end = Instant.parse("2025-01-01T00:00:00Z");
    assertNull(TimeUtils.formatPeriod(start, end));
  }

  @Test
  @DisplayName("formatPeriod computes correct short format")
  void formatPeriod_short() {
    LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
    LocalDateTime end = start
        .plusYears(1)
        .plusMonths(2)
        .plusDays(3)
        .plusHours(4)
        .plusMinutes(5);

    Instant startInstant = start.atZone(ZoneId.of("UTC")).toInstant();
    Instant endInstant = end.atZone(ZoneId.of("UTC")).toInstant();

    String result = TimeUtils.formatPeriod(startInstant, endInstant);
    assertEquals("1y, 2mo, 3d, 4h, 5m", result);
  }

  @Test
  @DisplayName("formatPeriod computes correct long format")
  void formatPeriod_long() {
    LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
    LocalDateTime end = start
        .plusYears(1)
        .plusMonths(1)
        .plusDays(1)
        .plusHours(1)
        .plusMinutes(1);

    Instant startInstant = start.atZone(ZoneId.of("UTC")).toInstant();
    Instant endInstant = end.atZone(ZoneId.of("UTC")).toInstant();

    String result = TimeUtils.formatPeriod(startInstant, endInstant, true);
    assertEquals("1 year, 1 month, 1 day, 1 hour, 1 minute", result);
  }

  @Test
  @DisplayName("formatDuration returns null if duration is null")
  void formatDuration_null() {
    assertNull(TimeUtils.formatDuration((Duration) null));
  }

  @Test
  @DisplayName("formatDuration contains expected components")
  void formatDuration_components() {
    Duration dur = Duration.ofDays(2).plusHours(3).plusMinutes(15);
    String result = TimeUtils.formatDuration(dur);
    assertNotNull(result, "Result should not be null");
    assertTrue(result.contains("2d"), "Should contain '2d'");
    assertTrue(result.contains("3h"), "Should contain '3h'");
    assertTrue(result.contains("15m"), "Should contain '15m'");
  }
}
