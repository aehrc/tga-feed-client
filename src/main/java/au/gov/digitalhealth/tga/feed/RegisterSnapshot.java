package au.gov.digitalhealth.tga.feed;

import au.gov.digitalhealth.tga.feed.model.RegisterEntry;
import au.gov.digitalhealth.tga.feed.model.RegisterProduct;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The register snapshot recorded on a ticket, used to detect whether an entry has since changed.
 *
 * <p><strong>This type's serialised shape is a cross-service contract, not an implementation
 * detail.</strong> A snapshot written by whichever service creates or updates a ticket is read back
 * and compared by the feed processor on every subsequent run. If the two disagree on field names,
 * the comparison always finds a difference and every affected ticket is re-flagged as changed on
 * every run — an endless update loop rather than a one-off glitch.
 *
 * <p>The field names below therefore reproduce exactly what is already stored on existing tickets:
 * <strong>camelCase at the top level</strong> (this type), with <strong>PascalCase nested
 * structures</strong> (the register's own naming, preserved verbatim). That mix looks inconsistent
 * because it is — it is the shape already in the database, and matching it is what keeps existing
 * tickets comparable.
 *
 * <p>Nested register structures are held as {@link JsonNode} rather than typed classes so that
 * fields one service does not model are still round-tripped rather than silently dropped from the
 * snapshot — a dropped field reads as a content change to whoever compares next.
 *
 * <p>Changing any name here requires a migration of stored snapshots or a versioning scheme. The
 * accompanying shape test exists to make an accidental change fail loudly.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "name",
  "consumerInformation",
  "productInformation",
  "blackTriangleSchemeFlag",
  "entryType",
  "products",
  "summaryUrl",
  "startDate",
  "licenceId",
  "status"
})
public class RegisterSnapshot {

  private String name;

  /** Consumer medicine information document, in the register's own structure. */
  private JsonNode consumerInformation;

  /** Product information document, in the register's own structure. */
  private JsonNode productInformation;

  private String blackTriangleSchemeFlag;

  private String entryType;

  /** Registered products, in the register's own structure. */
  private JsonNode products;

  /**
   * Public summary URL. Derived from the licence ID rather than returned by the register, but part
   * of the stored shape, so it must be populated or comparison will see a difference.
   */
  private String summaryUrl;

  private String startDate;

  private String licenceId;

  private String status;

  /**
   * Projects a freshly read register entry into the stored snapshot shape.
   *
   * <p>Shared because both services that write snapshots must write the <em>same</em> shape. They
   * previously each had their own projection, which is how a projection can drift into writing the
   * register's PascalCase field names where the stored shape is camelCase — a difference that makes
   * every subsequent comparison find a change and re-flag the ticket, forever.
   *
   * @param entry the entry as read from the register
   * @param summaryUrlTemplate template for the derived summary URL, see {@link
   *     TgaRegisterRules#summaryUrl(String, String)}
   * @param mapper used to hold the nested register structures verbatim, so fields this library does
   *     not model are round-tripped rather than dropped
   */
  public static RegisterSnapshot of(
      RegisterEntry entry, String summaryUrlTemplate, ObjectMapper mapper) {
    if (entry == null) {
      return null;
    }
    return RegisterSnapshot.builder()
        .name(entry.getName())
        .consumerInformation(nullSafeTree(mapper, entry.getConsumerInformation()))
        .productInformation(nullSafeTree(mapper, entry.getProductInformation()))
        .blackTriangleSchemeFlag(entry.getBlackTriangleSchemeFlag())
        .entryType(entry.getEntryType())
        .products(nullSafeTree(mapper, entry.getProducts()))
        // Derived rather than returned by the register, but part of the stored shape — omitting it
        // would itself read as a change.
        .summaryUrl(TgaRegisterRules.summaryUrl(summaryUrlTemplate, entry.getLicenceId()))
        .startDate(entry.getStartDate())
        .licenceId(entry.getLicenceId())
        .status(entry.getStatus())
        .build();
  }

  /** {@code valueToTree(null)} yields a NullNode; the stored shape omits absent values instead. */
  private static JsonNode nullSafeTree(ObjectMapper mapper, Object value) {
    return value == null ? null : mapper.valueToTree(value);
  }

  /**
   * Reads the registered products back out as typed products.
   *
   * <p>The inverse of what {@link #of} writes. Products are stored as JSON so that register fields
   * this library does not model survive the round trip, but a caller that wants to describe what
   * changed — which pack, which ingredient — needs them typed. Doing the conversion here keeps the
   * storage decision and the way out of it in one place.
   *
   * @return the products, or an empty list when the snapshot carries none
   */
  public List<RegisterProduct> productsAs(ObjectMapper mapper) {
    if (products == null || products.isNull() || products.isMissingNode()) {
      return List.of();
    }
    return mapper.convertValue(products, new TypeReference<List<RegisterProduct>>() {});
  }

  /**
   * As {@link #productsAs(ObjectMapper)}, for callers with no mapper to hand. Converting already
   * parsed JSON into these plain data types needs no application-specific mapper configuration.
   */
  public List<RegisterProduct> products() {
    return productsAs(DEFAULT_MAPPER);
  }

  private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper();
}
