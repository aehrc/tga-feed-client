package au.gov.digitalhealth.tga.feed;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;

/**
 * Facts derived from a register entry, independent of what any service does with them.
 *
 * <p>These are properties of the register itself — which entry types carry medicines, what the
 * black-triangle flag means, how a summary URL is formed — not decisions about tickets. Keeping
 * them here means the feed processor and the submission service read the register the same way,
 * rather than each interpreting it separately as they did before.
 */
public final class TgaRegisterRules {

  private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  /** Entry types that represent medicines, and so are in scope for submission processing. */
  private static final Set<String> MEDICINE_ENTRY_TYPES =
      Set.of("medicine listed", "medicine registered");

  private TgaRegisterRules() {}

  /**
   * Whether an entry type is a medicine registration.
   *
   * <p>Case-insensitive: the register's casing has not been stable enough to match exactly.
   */
  public static boolean isMedicineEntryType(String entryType) {
    return entryType != null && MEDICINE_ENTRY_TYPES.contains(entryType.trim().toLowerCase());
  }

  /** Whether the entry participates in the TGA Black Triangle Scheme. */
  public static boolean isBlackTriangle(String blackTriangleSchemeFlag) {
    return "Y".equalsIgnoreCase(blackTriangleSchemeFlag);
  }

  /**
   * The register's own summary URL form, as both services independently hardcoded it.
   *
   * <p>Offered as the canonical default so a consumer has something correct to fall back on and a
   * test has something real to use. Applications are still expected to make it configurable, since
   * the value is a published URL of an external system and can change without this library
   * changing.
   */
  public static final String SUMMARY_URL_TEMPLATE =
      "https://www.ebs.tga.gov.au/servlet/xmlmillr6?dbid=ebs/PublicHTML/pdfStore.nsf&docid=%d"
          + "&agid=(PrintDetailsPublic)&actionid=1";

  /**
   * Formats a register date for display on a ticket.
   *
   * <p>The register states dates as ISO {@code yyyy-MM-dd}; tickets show {@code dd/MM/yyyy}. Shared
   * because the converted value is written into a ticket field that is later compared — two services
   * formatting the same date differently would read as a change on every run.
   *
   * @return the formatted date, or null when absent or unparseable. Null rather than throwing: a
   *     register entry with no date is ordinary, and losing one field is better than losing the
   *     whole ticket.
   */
  public static String displayDate(String isoDate) {
    if (isoDate == null || isoDate.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(isoDate.trim()).format(DISPLAY_DATE);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /**
   * Builds the public summary URL for a registration.
   *
   * <p>The register does not return this — it is derived from the licence ID — so both services
   * previously built it from their own copy of the template.
   *
   * @param template format string taking the numeric licence ID, see {@link #SUMMARY_URL_TEMPLATE}
   * @return the URL, or null when the licence ID is absent or non-numeric
   */
  public static String summaryUrl(String template, String licenceId) {
    if (template == null || licenceId == null) {
      return null;
    }
    try {
      return String.format(template, Long.parseLong(licenceId.trim()));
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
