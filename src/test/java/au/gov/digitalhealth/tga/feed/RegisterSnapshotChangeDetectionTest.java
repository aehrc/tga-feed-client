package au.gov.digitalhealth.tga.feed;

import static org.assertj.core.api.Assertions.assertThat;

import au.gov.digitalhealth.tga.feed.model.RegisterEntry;
import au.gov.digitalhealth.tga.feed.model.RegisterSearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/**
 * Runs a real register entry through the same path a consumer uses: project it with {@link
 * RegisterSnapshot#of}, then compare it with the snapshot already stored on the ticket.
 *
 * <p>The stored snapshot is exactly as Lingo returned it for AMT-090058, keys in Postgres {@code
 * jsonb} order. The register entry is the same product read from the feed on the same day, with no
 * content change. Before field order was normalised, this pair was reported as changed on every
 * run, so the ticket was updated daily and reopened whenever someone closed it.
 */
class RegisterSnapshotChangeDetectionTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String SUMMARY_URL_TEMPLATE =
      "https://www.ebs.tga.gov.au/servlet/xmlmillr6?dbid=ebs/PublicHTML/pdfStore.nsf"
          + "&docid=%d&agid=(PrintDetailsPublic)&actionid=1";

  @Test
  void anUnchangedEntryReadBackFromStorageIsNotAChange() throws IOException {
    JsonNode stored = read("/amt-090058/stored-snapshot.json");
    RegisterEntry entry =
        MAPPER
            .treeToValue(read("/amt-090058/register-entry.json"), RegisterSearchResult.class)
            .getResults()
            .get(0);

    JsonNode fresh =
        MAPPER.valueToTree(RegisterSnapshot.of(entry, SUMMARY_URL_TEMPLATE, MAPPER));

    assertThat(RegisterSnapshotComparator.hasChanged(stored, fresh)).isFalse();
  }

  private static JsonNode read(String resource) throws IOException {
    try (InputStream in =
        RegisterSnapshotChangeDetectionTest.class.getResourceAsStream(resource)) {
      return MAPPER.readTree(in);
    }
  }
}
