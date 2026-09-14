package au.gov.digitalhealth.tga.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strips the register's meaningless variation out of a snapshot so two snapshots can be compared
 * for real content change.
 *
 * <p>The register does not return stable JSON for unchanged data. Three kinds of noise show up, and
 * a comparison that ignores them reports a change on almost every entry, on every run:
 *
 * <ul>
 *   <li><b>{@code EffectiveDate} churn.</b> The register revises this field on entries whose
 *       content is otherwise identical, so it cannot be treated as evidence of change.
 *   <li><b>Unstable collection order.</b> Components, containers, ingredients and packs come back
 *       in different orders for the same product.
 *   <li><b>Blank filler.</b> Entries carry empty strings, whitespace, and objects whose every field
 *       is blank, appearing and disappearing between reads.
 * </ul>
 *
 * <p>These rules are not an opinion about how a consumer wants to work — they are observations
 * about the register, learned the expensive way in the feed processor, where they lived inside a
 * DTO's {@code equals} and so applied only to whichever service happened to use that DTO. They
 * belong here, where every consumer gets them.
 *
 * <p>Normalising is deliberately done on JSON rather than on a typed entry. The stored snapshot's
 * shape is fixed by what is already written on every existing ticket, and a snapshot written by an
 * older version must still compare safely; going through a DTO would silently drop any register
 * field that DTO does not model, and a field that is dropped can never be detected as changed.
 */
public final class RegisterSnapshotNormaliser {

  /**
   * Fields excluded from comparison because the register revises them without the entry's content
   * changing. Matched case-insensitively, since the register's own casing is not consistent between
   * the wire shape ({@code EffectiveDate}) and the stored snapshot shape ({@code effectiveDate}).
   */
  private static final Set<String> VOLATILE_FIELDS = Set.of("effectivedate");

  private RegisterSnapshotNormaliser() {}

  /**
   * Returns a copy of {@code node} with volatile fields dropped, blanks removed, and arrays put in
   * a canonical order.
   *
   * <p>Every array is sorted, not only the collections of objects whose order is known to be
   * unstable. Sorting a list whose order happens to be stable costs only that a pure re-ordering of
   * its elements no longer reads as a change — which for register content is the same meaningless
   * churn this class exists to suppress. A change to any element is still detected either way, so
   * the conservative direction here is to sort.
   *
   * @param node a snapshot or freshly read entry, may be null
   * @return the normalised copy, or null when given null. The input is never modified.
   */
  public static JsonNode normalise(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return null;
    }
    return normaliseNode(node);
  }

  private static JsonNode normaliseNode(JsonNode node) {
    if (node.isObject()) {
      return normaliseObject(node);
    }
    if (node.isArray()) {
      return normaliseArray(node);
    }
    return node;
  }

  private static JsonNode normaliseObject(JsonNode node) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> field = fields.next();
      if (VOLATILE_FIELDS.contains(field.getKey().toLowerCase())) {
        continue;
      }
      JsonNode value = normaliseNode(field.getValue());
      if (isBlank(value)) {
        continue;
      }
      result.set(field.getKey(), value);
    }
    return result;
  }

  private static JsonNode normaliseArray(JsonNode node) {
    List<JsonNode> kept = new ArrayList<>();
    for (JsonNode child : node) {
      JsonNode normalised = normaliseNode(child);
      if (!isBlank(normalised)) {
        kept.add(normalised);
      }
    }
    // Canonical order by serialised form: stable, and needs no knowledge of the element's shape.
    kept.sort(Comparator.comparing(JsonNode::toString));

    ArrayNode result = JsonNodeFactory.instance.arrayNode(kept.size());
    kept.forEach(result::add);
    return result;
  }

  /**
   * Whether a value carries no content: null, a blank string, or a collection or object left with
   * nothing in it once its own blanks were removed.
   *
   * <p>Emptiness is recursive because the register's filler is recursive — an object whose every
   * field is an empty string is as absent as a missing object, and treating the two differently is
   * exactly what makes an unchanged entry look changed.
   */
  private static boolean isBlank(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return true;
    }
    if (node.isTextual()) {
      return node.asText().trim().isEmpty();
    }
    if (node.isArray() || node.isObject()) {
      return node.isEmpty();
    }
    return false;
  }
}
