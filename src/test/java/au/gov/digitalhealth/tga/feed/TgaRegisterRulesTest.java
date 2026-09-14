package au.gov.digitalhealth.tga.feed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the register-domain rules both services rely on.
 *
 * <p>These were previously implemented separately in the feed processor and the submission service.
 * The values asserted here are the behaviour the feed processor produced, since that is what every
 * existing ticket was built from.
 */
class TgaRegisterRulesTest {

  private static final String SUMMARY_TEMPLATE =
      "https://www.ebs.tga.gov.au/servlet/xmlmillr6?docid=%d&agid=(PrintDetailsPublic)";

  // ── schedules ────────────────────────────────────────────────────────────────

  @Test
  void extractsScheduleCodeFromFreeText() {
    assertThat(TgaScheduleRules.extractCode("Schedule S8 (Controlled Drug)")).isEqualTo("S8");
    assertThat(TgaScheduleRules.extractCode("S4")).isEqualTo("S4");
  }

  @Test
  void unscheduledTextYieldsNone() {
    assertThat(TgaScheduleRules.extractCode("Unscheduled")).isEqualTo("None");
    assertThat(TgaScheduleRules.extractCode("")).isEqualTo("None");
    assertThat(TgaScheduleRules.extractCode(null)).isEqualTo("None");
  }

  @Test
  void highestSchedulePicksLargestNumber() {
    assertThat(TgaScheduleRules.highest(List.of("S2", "S8", "S4"))).isEqualTo("S8");
  }

  @Test
  void anyScheduleOutranksNone() {
    assertThat(TgaScheduleRules.highest(List.of("Unscheduled", "S2"))).isEqualTo("S2");
  }

  @Test
  void allUnscheduledYieldsNone() {
    assertThat(TgaScheduleRules.highest(List.of("Unscheduled", ""))).isEqualTo("None");
    assertThat(TgaScheduleRules.highest(List.of())).isEqualTo("None");
    assertThat(TgaScheduleRules.highest(null)).isEqualTo("None");
  }

  @Test
  void doubleDigitSchedulesCompareNumericallyNotLexically() {
    // "S10" must beat "S9" — string ordering would get this wrong.
    assertThat(TgaScheduleRules.highest(List.of("S9", "S10"))).isEqualTo("S10");
  }

  @Test
  void ranksHighestFirst() {
    assertThat(TgaScheduleRules.rankedHighestFirst(List.of("S2", "Unscheduled", "S8")))
        .containsExactly("S8", "S2", "None");
  }

  // ── entry classification ─────────────────────────────────────────────────────

  @Test
  void recognisesMedicineEntryTypes() {
    assertThat(TgaRegisterRules.isMedicineEntryType("Medicine Listed")).isTrue();
    assertThat(TgaRegisterRules.isMedicineEntryType("Medicine Registered")).isTrue();
  }

  @Test
  void entryTypeMatchingToleratesCasingAndPadding() {
    assertThat(TgaRegisterRules.isMedicineEntryType("  medicine listed  ")).isTrue();
    assertThat(TgaRegisterRules.isMedicineEntryType("MEDICINE REGISTERED")).isTrue();
  }

  @Test
  void rejectsNonMedicineEntryTypes() {
    assertThat(TgaRegisterRules.isMedicineEntryType("Device Included")).isFalse();
    assertThat(TgaRegisterRules.isMedicineEntryType(null)).isFalse();
  }

  @Test
  void blackTriangleFlagIsCaseInsensitive() {
    assertThat(TgaRegisterRules.isBlackTriangle("Y")).isTrue();
    assertThat(TgaRegisterRules.isBlackTriangle("y")).isTrue();
    assertThat(TgaRegisterRules.isBlackTriangle("N")).isFalse();
    assertThat(TgaRegisterRules.isBlackTriangle(null)).isFalse();
  }

  // ── summary URL ──────────────────────────────────────────────────────────────

  @Test
  void buildsSummaryUrlFromLicenceId() {
    assertThat(TgaRegisterRules.summaryUrl(SUMMARY_TEMPLATE, "123456")).contains("docid=123456");
  }

  @Test
  void summaryUrlIsNullForUnusableLicenceId() {
    assertThat(TgaRegisterRules.summaryUrl(SUMMARY_TEMPLATE, "not-a-number")).isNull();
    assertThat(TgaRegisterRules.summaryUrl(SUMMARY_TEMPLATE, null)).isNull();
    assertThat(TgaRegisterRules.summaryUrl(null, "123456")).isNull();
  }

  // ── change detection ─────────────────────────────────────────────────────────

  @Test
  void identicalSnapshotsAreUnchanged() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    var stored = mapper.readTree("{\"licenceId\":\"1\",\"name\":\"X\"}");
    var fresh = mapper.readTree("{\"licenceId\":\"1\",\"name\":\"X\"}");

    assertThat(RegisterSnapshotComparator.hasChanged(stored, fresh)).isFalse();
  }

  @Test
  void fieldOrderAloneIsNotAChange() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    var stored = mapper.readTree("{\"licenceId\":\"1\",\"name\":\"X\"}");
    var reordered = mapper.readTree("{\"name\":\"X\",\"licenceId\":\"1\"}");

    // Serialisation order must never be mistaken for a content change — that would re-flag the
    // entire register on the first run after any ordering change.
    assertThat(RegisterSnapshotComparator.hasChanged(stored, reordered)).isFalse();
  }

  @Test
  void differingValueIsAChange() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    var stored = mapper.readTree("{\"licenceId\":\"1\",\"name\":\"Old\"}");
    var fresh = mapper.readTree("{\"licenceId\":\"1\",\"name\":\"New\"}");

    assertThat(RegisterSnapshotComparator.hasChanged(stored, fresh)).isTrue();
  }

  @Test
  void missingStoredSnapshotCountsAsChanged() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    var fresh = mapper.readTree("{\"licenceId\":\"1\"}");

    assertThat(RegisterSnapshotComparator.hasChanged(null, fresh)).isTrue();
  }

  @Test
  void missingFreshEntryIsNotTreatedAsChanged() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    var stored = mapper.readTree("{\"licenceId\":\"1\"}");

    // No fresh data is not evidence of change; rewriting from data we do not have would be worse.
    assertThat(RegisterSnapshotComparator.hasChanged(stored, null)).isFalse();
  }

  // ── description ──────────────────────────────────────────────────────────────

  @Test
  void descriptionRendersRegisterRows() {
    String html =
        TgaDescriptionBuilder.builder()
            .summaryUrl("https://example.test/summary")
            .consumerInformationUrl("https://example.test/cmi.pdf")
            .productInformationUrl("https://example.test/pi.pdf")
            .entryType("Medicine Registered")
            .build()
            .createTable();

    assertThat(html)
        .contains("ARTG Summary URL")
        .contains("TGA entry type")
        .contains("Medicine Registered")
        .contains("Consumer Information URL")
        .contains("Product Information Url");
  }

  @Test
  void descriptionOmitsDocumentRowsWhenLinksAbsent() {
    String html =
        TgaDescriptionBuilder.builder()
            .summaryUrl("https://example.test/summary")
            .entryType("Medicine Listed")
            .build()
            .createTable();

    assertThat(html)
        .contains("ARTG Summary URL")
        .doesNotContain("Consumer Information URL")
        .doesNotContain("Product Information Url");
  }
}
