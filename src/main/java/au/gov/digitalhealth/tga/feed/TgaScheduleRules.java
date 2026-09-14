package au.gov.digitalhealth.tga.feed;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Poison-schedule rules from the TGA register.
 *
 * <p>Deliberately expressed over plain strings rather than an entry type, so every service can use
 * these rules while keeping its own representation of a register entry. That keeps this module free
 * of the entry DTO, whose serialised form is stored on tickets and cannot be changed casually.
 */
public final class TgaScheduleRules {

  /** Returned when a pack carries no recognisable schedule code. */
  public static final String NO_SCHEDULE = "None";

  private static final Pattern SCHEDULE_PATTERN = Pattern.compile("S(\\d+)");

  private TgaScheduleRules() {}

  /**
   * Extracts a schedule code such as {@code S8} from the register's free-text poison-schedule
   * value.
   *
   * @return the code, or {@link #NO_SCHEDULE} when the text contains none
   */
  public static String extractCode(String poisonSchedule) {
    if (poisonSchedule == null) {
      return NO_SCHEDULE;
    }
    Matcher matcher = SCHEDULE_PATTERN.matcher(poisonSchedule);
    return matcher.find() ? "S" + matcher.group(1) : NO_SCHEDULE;
  }

  /**
   * The highest schedule across a set of pack values, higher numbers ranking above lower and any
   * code ranking above {@link #NO_SCHEDULE}.
   *
   * @param poisonSchedules raw values as they appear on the register's packs
   */
  public static String highest(Collection<String> poisonSchedules) {
    if (poisonSchedules == null || poisonSchedules.isEmpty()) {
      return NO_SCHEDULE;
    }
    return poisonSchedules.stream()
        .map(TgaScheduleRules::extractCode)
        .max(comparator())
        .orElse(NO_SCHEDULE);
  }

  /** Orders schedule codes by their numeric part, with {@link #NO_SCHEDULE} always lowest. */
  public static Comparator<String> comparator() {
    return (a, b) -> {
      boolean aNone = NO_SCHEDULE.equals(a);
      boolean bNone = NO_SCHEDULE.equals(b);
      if (aNone && bNone) {
        return 0;
      }
      if (aNone) {
        return -1;
      }
      if (bNone) {
        return 1;
      }
      return Integer.compare(numericPart(a), numericPart(b));
    };
  }

  private static int numericPart(String code) {
    return Integer.parseInt(code.substring(1));
  }

  /** Convenience for callers holding an ordered list they want ranked highest-first. */
  public static List<String> rankedHighestFirst(Collection<String> poisonSchedules) {
    return poisonSchedules.stream()
        .map(TgaScheduleRules::extractCode)
        .sorted(comparator().reversed())
        .toList();
  }
}
