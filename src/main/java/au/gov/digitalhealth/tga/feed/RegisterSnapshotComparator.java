package au.gov.digitalhealth.tga.feed;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Decides whether a register entry has changed since it was last recorded.
 *
 * <p>Compares the stored snapshot against a freshly observed entry as JSON rather than as a typed
 * entry. That is deliberate: the stored snapshot's shape is fixed by what is already written on
 * every existing ticket, so comparison must not depend on a DTO whose fields might be renamed or
 * reordered later. Working in JSON also lets a stored snapshot written by an older version still be
 * compared safely.
 *
 * <p>Comparison is order-insensitive for object fields — Jackson's {@code JsonNode.equals} treats
 * objects as unordered maps — so a change in serialisation order alone does not read as a content
 * change.
 *
 * <p>Both sides are put through {@link RegisterSnapshotNormaliser} first. Without that the
 * register's own churn — a revised {@code EffectiveDate}, a re-ordered ingredient list, blank
 * filler that comes and goes — reads as a content change, and almost every entry looks changed on
 * almost every run.
 */
public final class RegisterSnapshotComparator {

  private RegisterSnapshotComparator() {}

  /**
   * Whether the freshly observed entry differs from what was last stored.
   *
   * @param storedSnapshot the snapshot recorded on the ticket, or null when none has been recorded
   * @param freshEntry the entry as just read from the register
   * @return true when the entry should be treated as changed. A missing stored snapshot counts as
   *     changed, so an entry that has never been recorded is always processed rather than silently
   *     skipped.
   */
  public static boolean hasChanged(JsonNode storedSnapshot, JsonNode freshEntry) {
    if (storedSnapshot == null || storedSnapshot.isNull() || storedSnapshot.isMissingNode()) {
      return true;
    }
    if (freshEntry == null || freshEntry.isNull() || freshEntry.isMissingNode()) {
      // Nothing meaningful to compare against; treat as unchanged rather than triggering a
      // rewrite from data we do not have.
      return false;
    }
    return !RegisterSnapshotNormaliser.normalise(storedSnapshot)
        .equals(RegisterSnapshotNormaliser.normalise(freshEntry));
  }
}
