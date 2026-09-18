package au.gov.digitalhealth.tga.feed.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Verifies the shared register model parses the register's JSON, including the fields that only one
 * consuming service modelled before consolidation.
 *
 * <p>The union matters: whichever service writes a ticket's register snapshot writes what it
 * parsed, so a field this model drops disappears from the snapshot — and its absence reads as a
 * content change to whichever service compares next.
 */
class RegisterEntryParsingTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private static final String REGISTER_JSON =
      """
      {
        "LicenceId": "123456",
        "Name": "Ibuprofen 200mg Tablet",
        "EntryType": "Medicine Registered",
        "Status": "Active",
        "StartDate": "2010-01-01",
        "BlackTriangleSchemeFlag": "Y",
        "ProductCategory": "Medicine",
        "ApprovalArea": "Prescription medicines",
        "Sponsor": { "Name": "Acme Pharma", "Address": { "Suburb": "Parkville", "State": "VIC" } },
        "ConsumerInformation": { "DocumentLink": "https://example.test/cmi.pdf" },
        "ProductInformation": { "DocumentLink": "https://example.test/pi.pdf" },
        "Products": [
          {
            "Name": "Ibuprofen",
            "Type": "Medicine",
            "GMDNCode": "12345",
            "GMDNTerm": "Analgesic",
            "SpecificIndications": ["Pain relief"],
            "Ingredients": [{ "Name": "Ibuprofen", "Strength": "200mg" }],
            "Components": [{ "DosageForm": "Tablet" }],
            "Containers": [{ "Type": "Blister", "Conditions": ["Store below 25C"] }],
            "Packs": [{ "Size": "30", "PoisonSchedule": "S4" }]
          }
        ]
      }
      """;

  @Test
  void parsesTheFieldsBothServicesShare() throws Exception {
    RegisterEntry entry = mapper.readValue(REGISTER_JSON, RegisterEntry.class);

    assertThat(entry.getLicenceId()).isEqualTo("123456");
    assertThat(entry.getName()).isEqualTo("Ibuprofen 200mg Tablet");
    assertThat(entry.getEntryType()).isEqualTo("Medicine Registered");
    assertThat(entry.getBlackTriangleSchemeFlag()).isEqualTo("Y");
    assertThat(entry.getConsumerInformation().getDocumentLink())
        .isEqualTo("https://example.test/cmi.pdf");
    assertThat(entry.getProducts().get(0).getPacks().get(0).getPoisonSchedule()).isEqualTo("S4");
  }

  @Test
  void parsesFieldsOnlyTheFeedProcessorModelledBefore() throws Exception {
    RegisterEntry entry = mapper.readValue(REGISTER_JSON, RegisterEntry.class);
    RegisterProduct product = entry.getProducts().get(0);

    // Absent from the submission service's own entry type — losing these on write would have
    // shown up later as a spurious content change.
    assertThat(product.getGmdnCode()).isEqualTo("12345");
    assertThat(product.getGmdnTerm()).isEqualTo("Analgesic");
    assertThat(product.getSpecificIndications()).containsExactly("Pain relief");
    assertThat(entry.getSponsor().getAddress().getState()).isEqualTo("VIC");
  }

  @Test
  void parsesFieldsOnlyTheSubmissionServiceModelledBefore() throws Exception {
    RegisterEntry entry = mapper.readValue(REGISTER_JSON, RegisterEntry.class);

    assertThat(entry.getApprovalArea()).isEqualTo("Prescription medicines");
    assertThat(entry.getProducts().get(0).getComponents().get(0).getDosageForm())
        .isEqualTo("Tablet");
    assertThat(entry.getProducts().get(0).getContainers().get(0).getConditions())
        .containsExactly("Store below 25C");
  }

  @Test
  void productCategoryStaysARegisterString() throws Exception {
    // Not an enum here — mapping it to a service's internal product model is that service's job.
    RegisterEntry entry = mapper.readValue(REGISTER_JSON, RegisterEntry.class);

    assertThat(entry.getProductCategory()).isEqualTo("Medicine");
  }

  @Test
  void unknownRegisterFieldsAreIgnoredRatherThanFailing() throws Exception {
    String withNewField = "{\"LicenceId\":\"1\",\"SomeFieldTheRegisterAddedLater\":\"x\"}";

    RegisterEntry entry = mapper.readValue(withNewField, RegisterEntry.class);

    assertThat(entry.getLicenceId()).isEqualTo("1");
  }

  @Test
  void parsesASearchResultPage() throws Exception {
    String page =
        "{\"TotalRecords\":2,\"RequestedPageStart\":1,\"RequestedPageEnd\":100,"
            + "\"Results\":["
            + REGISTER_JSON
            + "]}";

    RegisterSearchResult result = mapper.readValue(page, RegisterSearchResult.class);

    assertThat(result.getTotalRecords()).isEqualTo(2);
    assertThat(result.getResults()).hasSize(1);
    assertThat(result.getResults().get(0).getLicenceId()).isEqualTo("123456");
  }

  /**
   * Verbatim from the register: a {@code Medical Device Included} entry whose DeviceProductNames are
   * objects. Modelling them as strings parsed every medicine page and then failed on the first
   * device entry carrying one — about two per thousand, so a whole scan reached page 3000 before
   * anything went wrong. Nothing here reads these names; the entry only has to parse, because one
   * unparseable entry fails the page it sits in and, with it, the rest of the scan.
   */
  @Test
  void parsesADeviceEntryWhoseProductNamesAreObjects() throws Exception {
    String deviceEntry =
        """
        {
          "LicenceId": "104526",
          "Name": "Johnson & Johnson Medical - spinal implant",
          "EntryType": "Medical Device Included",
          "Status": "Active",
          "DeviceProductNames": [
            { "Name": "Anterior ISOLA Spine System single hole washer" },
            { "Name": "CrossOver large cross connector" }
          ]
        }
        """;

    RegisterEntry entry = mapper.readValue(deviceEntry, RegisterEntry.class);

    assertThat(entry.getDeviceProductNames()).hasSize(2);
    assertThat(entry.getDeviceProductNames().get(0).getName())
        .isEqualTo("Anterior ISOLA Spine System single hole washer");
  }

  /** The shape the other 998 entries in a page carry. */
  @Test
  void parsesAnEntryWithNoDeviceProductNames() throws Exception {
    RegisterEntry entry =
        mapper.readValue("{\"LicenceId\":\"1\",\"DeviceProductNames\":[]}", RegisterEntry.class);

    assertThat(entry.getDeviceProductNames()).isEmpty();
  }
}
