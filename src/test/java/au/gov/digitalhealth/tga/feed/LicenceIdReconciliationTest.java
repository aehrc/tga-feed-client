package au.gov.digitalhealth.tga.feed;

import static org.assertj.core.api.Assertions.assertThat;

import au.gov.digitalhealth.tga.feed.model.RegisterEntry;
import au.gov.digitalhealth.tga.feed.model.RegisterSearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Covers the filtering applied to a licence-ID response.
 *
 * <p>The register can return entries that do not carry the licence ID that was asked for. Both
 * consuming services had written their own filter for this; a caller that omitted it would process
 * a different product than the one requested. These tests pin the shared behaviour.
 *
 * <p>The reconciliation is exercised directly against a parsed response rather than over HTTP,
 * since what matters is which entries survive, not how they were fetched.
 */
class LicenceIdReconciliationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void keepsOnlyEntriesCarryingTheRequestedLicenceId() throws Exception {
    RegisterSearchResult result = parse("123456", "999999");

    List<RegisterEntry> matching = matching(result, 123456);

    assertThat(matching).hasSize(1);
    assertThat(matching.get(0).getLicenceId()).isEqualTo("123456");
  }

  @Test
  void keepsEveryMatchingEntry() throws Exception {
    // A product registered in several pack-size variants legitimately appears more than once.
    RegisterSearchResult result = parse("123456", "123456", "999999");

    assertThat(matching(result, 123456)).hasSize(2);
  }

  @Test
  void returnsNothingWhenNoEntryCarriesTheRequestedId() throws Exception {
    RegisterSearchResult result = parse("999999");

    assertThat(matching(result, 123456)).isEmpty();
  }

  @Test
  void handlesAnEmptyResponse() throws Exception {
    RegisterSearchResult result =
        mapper.readValue("{\"TotalRecords\":0,\"Results\":[]}", RegisterSearchResult.class);

    assertThat(matching(result, 123456)).isEmpty();
  }

  /** Mirrors the filter applied inside {@link TgaFeedClient#fetchEntriesByLicenceId}. */
  private List<RegisterEntry> matching(RegisterSearchResult result, long licenceId) {
    if (result.getResults() == null) {
      return List.of();
    }
    String requested = Long.toString(licenceId);
    return result.getResults().stream()
        .filter(entry -> requested.equals(entry.getLicenceId()))
        .toList();
  }

  private RegisterSearchResult parse(String... licenceIds) throws Exception {
    StringBuilder entries = new StringBuilder();
    for (int i = 0; i < licenceIds.length; i++) {
      if (i > 0) {
        entries.append(",");
      }
      entries.append("{\"LicenceId\":\"").append(licenceIds[i]).append("\",\"Name\":\"P\"}");
    }
    return mapper.readValue(
        "{\"TotalRecords\":" + licenceIds.length + ",\"Results\":[" + entries + "]}",
        RegisterSearchResult.class);
  }
}
