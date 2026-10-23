package com.Lomikel.Utils;

import java.util.TimeZone;
import java.util.Locale;

/** Offline regression tests; run with the focused javac/java command. */
public class DateTimeManagementTest {
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static void customFormat() {
    check("1970-01-02".equals(DateTimeManagement.time2String(86_400_000L, "yyyy-MM-dd")),
          "nonempty custom format was ignored");
    check(DateTimeManagement.string2time("2024-02-29", "yyyy-MM-dd") == 1_709_164_800_000L,
          "valid leap day rejected");
    check(DateTimeManagement.string2julianDate("2000-01-01", "yyyy-MM-dd") > 2_451_543,
          "valid custom Julian date rejected");
  }

  private static void callerNanosecondFormats() {
    check("1970 01 02 00:00:00.123000000".equals(
          DateTimeManagement.time2String(86_400_123L, "yyyy MM dd HH:mm:ss.nnnnnnnnn")),
          "HBaseColumnsProcessor nine-digit fraction must extend milliseconds");
    check("00:00:00.123000000 02/01/1970 ".equals(
          DateTimeManagement.time2String(86_400_123L, "HH:mm:ss.nnnnnnnnn dd/MM/yyyy ")),
          "PropertiesProcessor nine-digit fraction must extend milliseconds");
  }

  private static void callerCalendarYear() {
    check(DateTimeManagement.string2time("23/10/2026 12:34", "dd/MM/YYYY HH:mm")
          == 1_792_758_840_000L, "HBaseTable week-year must parse as calendar year");
    for (String input : new String[]{"31/02/2026 12:34", "23/10/2026 12:34suffix"}) {
      try {
        DateTimeManagement.string2time(input, "dd/MM/YYYY HH:mm");
        throw new AssertionError("Invalid HBaseTable time accepted: " + input);
      } catch (IllegalArgumentException expected) {
        check(expected.getMessage().contains(input), "error must identify HBaseTable input");
      }
    }
  }

  private static void unsupportedPatternIsNotRewritten() {
    try {
      DateTimeManagement.time2String(86_400_123L, "yyyy-MM-dd nnnnnnnnn");
      throw new AssertionError("unsupported n pattern must not be silently rewritten");
    } catch (IllegalArgumentException expected) {
      check(expected.getMessage().contains("n"), "unsupported pattern must remain invalid");
    }
  }

  private static void invalidTime() {
    for (String input : new String[]{"not-a-date", "2024-02-30", "2024-02-29suffix"}) {
      try {
        DateTimeManagement.string2time(input, "yyyy-MM-dd");
        throw new AssertionError("Invalid time accepted: " + input);
      } catch (IllegalArgumentException expected) {
        check(expected.getMessage().contains(input), "error must identify the input");
      }
    }
  }

  private static void invalidJulianDate() {
    for (String input : new String[]{"not-a-date", "2024-02-30", "2024-02-29suffix"}) {
      try {
        DateTimeManagement.string2julianDate(input, "yyyy-MM-dd");
        throw new AssertionError("Invalid Julian input accepted: " + input);
      } catch (IllegalArgumentException expected) {
        check(expected.getMessage().contains(input), "error must identify the input");
      }
    }
  }

  private static void millisecondsAndLegacyDefaults() {
    check(DateTimeManagement.string2time("1970-01-02", "yyyy-MM-dd") == 86_400_000L,
          "string2time must return milliseconds, not nanoseconds");
    check(DateTimeManagement.time2String(86_400_000L).equals("00:00:00.000 02/Jan/1970"),
          "default format changed");
    check(DateTimeManagement.time2String(86_400_000L, " ").equals(DateTimeManagement.time2String(86_400_000L)),
          "blank format must use default");
    long before = System.currentTimeMillis();
    for (String input : new String[]{null, "  "}) {
      long value = DateTimeManagement.string2time(input);
      check(value >= before && value <= System.currentTimeMillis(), "blank input must mean now");
    }
    // The zero timestamp remains the legacy "now" sentinel, not the Unix epoch.
    check(!"1970".equals(DateTimeManagement.time2String(0, "yyyy")), "zero sentinel changed");
    check(DateTimeManagement.string2julianDate(null) > 2_000_000,
          "null Julian input must retain its current-time behavior");
  }

  public static void main(String[] args) {
    TimeZone previous = TimeZone.getDefault();
    Locale previousLocale = Locale.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
      Locale.setDefault(Locale.US);
      customFormat();
      callerNanosecondFormats();
      callerCalendarYear();
      unsupportedPatternIsNotRewritten();
      invalidTime();
      invalidJulianDate();
      millisecondsAndLegacyDefaults();
      System.out.println("DateTimeManagementTest passed");
    } finally {
      TimeZone.setDefault(previous);
      Locale.setDefault(previousLocale);
    }
  }
}
