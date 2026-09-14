package au.gov.digitalhealth.tga.feed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the serialised shape of {@link RegisterSnapshot}.
 *
 * <p>These assertions look pedantic on purpose. The snapshot's field names are a contract with
 * every ticket already stored: the writer serialises them, and the feed processor deserialises and
 * compares them on every run. Rename one and the comparison can never match again, so every
 * affected ticket is reported as changed on every run — a permanent update loop, not a visible
 * failure. A test that fails on a rename is the cheapest way to catch that.
 */
class RegisterSnapshotShapeTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void topLevelFieldNamesAreCamelCase() {
    JsonNode json = mapper.valueToTree(sampleSnapshot());

    // Exactly as already stored — NOT the register's own PascalCase.
    assertThat(json.has("licenceId")).isTrue();
    assertThat(json.has("name")).isTrue();
    assertThat(json.has("entryType")).isTrue();
    assertThat(json.has("blackTriangleSchemeFlag")).isTrue();
    assertThat(json.has("summaryUrl")).isTrue();
    assertThat(json.has("startDate")).isTrue();
    assertThat(json.has("status")).isTrue();

    assertThat(json.has("LicenceId")).as("PascalCase would break every stored snapshot").isFalse();
    assertThat(json.has("Name")).isFalse();
  }

  @Test
  void nestedStructuresKeepTheRegistersOwnNaming() {
    JsonNode json = mapper.valueToTree(sampleSnapshot());

    // Nested register structures stay PascalCase — the shape the register itself returns.
    assertThat(json.path("products").get(0).has("Name")).isTrue();
    assertThat(json.path("products").get(0).path("Packs").get(0).has("PoisonSchedule")).isTrue();
  }

  @Test
  void unmodelledNestedFieldsSurviveRoundTrip() throws Exception {
    // A field one service does not model must still be preserved — dropping it would read as a
    // content change to whichever service compares next.
    String stored = "{\"licenceId\":\"1\",\"products\":[{\"Name\":\"P\",\"GMDNCode\":\"12345\"}]}";

    RegisterSnapshot snapshot = mapper.readValue(stored, RegisterSnapshot.class);
    JsonNode reserialised = mapper.valueToTree(snapshot);

    assertThat(reserialised.path("products").get(0).path("GMDNCode").asText()).isEqualTo("12345");
  }

  @Test
  void snapshotRoundTripsUnchanged() throws Exception {
    RegisterSnapshot original = sampleSnapshot();

    String json = mapper.writeValueAsString(original);
    RegisterSnapshot restored = mapper.readValue(json, RegisterSnapshot.class);

    // Re-serialising a snapshot we just read must produce identical JSON, or comparison would
    // report a change purely from having been round-tripped.
    JsonNode before = mapper.valueToTree(original);
    JsonNode after = mapper.valueToTree(restored);
    assertThat(after).isEqualTo(before);
  }

  @Test
  void nullFieldsAreOmittedNotWrittenAsNull() {
    RegisterSnapshot sparse = RegisterSnapshot.builder().licenceId("1").build();

    JsonNode json = mapper.valueToTree(sparse);

    assertThat(json.has("licenceId")).isTrue();
    assertThat(json.has("name")).isFalse();
  }

  @Test
  void aRoundTrippedSnapshotComparesEqualToItself() {
    RegisterSnapshot snapshot = sampleSnapshot();

    assertThat(
            RegisterSnapshotComparator.hasChanged(
                mapper.valueToTree(snapshot), mapper.valueToTree(snapshot)))
        .isFalse();
  }

  private RegisterSnapshot sampleSnapshot() {
    ObjectNode pack = mapper.createObjectNode();
    pack.put("PoisonSchedule", "S4");
    pack.put("Size", "30");

    ObjectNode product = mapper.createObjectNode();
    product.put("Name", "Ibuprofen 200mg Tablet");
    product.set("Packs", mapper.valueToTree(List.of(pack)));

    return RegisterSnapshot.builder()
        .licenceId("123456")
        .name("Ibuprofen 200mg Tablet")
        .entryType("Medicine Registered")
        .blackTriangleSchemeFlag("N")
        .summaryUrl("https://example.test/summary")
        .startDate("2010-01-01")
        .status("Active")
        .products(mapper.valueToTree(List.of(product)))
        .build();
  }
}
