package au.gov.digitalhealth.tga.feed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * These pin the register's noise rules that previously lived inside the feed processor's {@code
 * Product.equals}. They existed there for a reason — each one suppresses variation the register
 * really produces — so each is pinned here rather than re-derived.
 *
 * <p>A regression in any of them is expensive and quiet: change detection starts reporting every
 * entry as changed on every run, and the register gets hammered while tickets churn.
 */
class RegisterSnapshotNormaliserTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static JsonNode json(String raw) {
    try {
      return MAPPER.readTree(raw);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private static boolean changed(String stored, String fresh) {
    return RegisterSnapshotComparator.hasChanged(json(stored), json(fresh));
  }

  // ── EffectiveDate churn ─────────────────────────────────────────────────────

  @Test
  void aRevisedEffectiveDateAloneIsNotAChange() {
    assertThat(
            changed(
                "{\"name\":\"Aspirin\",\"products\":[{\"EffectiveDate\":\"2024-01-01\"}]}",
                "{\"name\":\"Aspirin\",\"products\":[{\"EffectiveDate\":\"2025-06-30\"}]}"))
        .isFalse();
  }

  @Test
  void effectiveDateIsIgnoredWhateverItsCasing() {
    // The wire shape spells it EffectiveDate; the stored snapshot shape spells it effectiveDate.
    assertThat(changed("{\"effectiveDate\":\"a\"}", "{\"EffectiveDate\":\"b\"}")).isFalse();
  }

  @Test
  void aRealChangeAlongsideAnEffectiveDateRevisionIsStillDetected() {
    assertThat(
            changed(
                "{\"name\":\"Aspirin\",\"products\":[{\"EffectiveDate\":\"2024-01-01\"}]}",
                "{\"name\":\"Aspirin 100mg\",\"products\":[{\"EffectiveDate\":\"2025-06-30\"}]}"))
        .isTrue();
  }

  // ── Unstable collection order ───────────────────────────────────────────────

  @Test
  void reorderedIngredientsAreNotAChange() {
    assertThat(
            changed(
                "{\"products\":[{\"Ingredients\":[{\"Name\":\"A\"},{\"Name\":\"B\"}]}]}",
                "{\"products\":[{\"Ingredients\":[{\"Name\":\"B\"},{\"Name\":\"A\"}]}]}"))
        .isFalse();
  }

  @Test
  void aChangedIngredientIsStillDetectedDespiteReordering() {
    assertThat(
            changed(
                "{\"products\":[{\"Ingredients\":[{\"Name\":\"A\"},{\"Name\":\"B\"}]}]}",
                "{\"products\":[{\"Ingredients\":[{\"Name\":\"B\"},{\"Name\":\"C\"}]}]}"))
        .isTrue();
  }

  @Test
  void aRemovedIngredientIsAChangeEvenThoughOrderIsIgnored() {
    assertThat(
            changed(
                "{\"products\":[{\"Ingredients\":[{\"Name\":\"A\"},{\"Name\":\"B\"}]}]}",
                "{\"products\":[{\"Ingredients\":[{\"Name\":\"A\"}]}]}"))
        .isTrue();
  }

  // ── Blank filler ────────────────────────────────────────────────────────────

  @Test
  void blankStringsAppearingOrDisappearingAreNotAChange() {
    assertThat(changed("{\"name\":\"Aspirin\",\"status\":\"\"}", "{\"name\":\"Aspirin\"}"))
        .isFalse();
  }

  @Test
  void whitespaceOnlyValuesCountAsBlank() {
    assertThat(changed("{\"name\":\"Aspirin\",\"status\":\"   \"}", "{\"name\":\"Aspirin\"}"))
        .isFalse();
  }

  @Test
  void anAllBlankContainerIsTreatedAsAbsent() {
    // The register emits containers whose every field is empty; they come and go between reads.
    assertThat(
            changed(
                "{\"products\":[{\"Containers\":[{\"Closure\":\"\",\"Type\":\"  \"}]}]}",
                "{\"products\":[{\"Containers\":[]}]}"))
        .isFalse();
  }

  @Test
  void anAllBlankEntryNestedSeveralLevelsDeepIsTreatedAsAbsent() {
    assertThat(changed("{\"a\":{\"b\":{\"c\":[\"\",\"  \"]}}}", "{}")).isFalse();
  }

  @Test
  void aBlankValueBecomingRealContentIsAChange() {
    assertThat(
            changed(
                "{\"name\":\"Aspirin\",\"status\":\"\"}",
                "{\"name\":\"Aspirin\",\"status\":\"Cancelled\"}"))
        .isTrue();
  }

  // ── Fields that must participate ────────────────────────────────────────────

  @Test
  void aBlackTriangleFlagChangeIsDetected() {
    // The feed processor's typed comparison omitted this field from equals while including it in
    // hashCode, so a black-triangle change alone never triggered an update. Comparing the whole
    // snapshot means every field participates and that class of omission cannot recur.
    assertThat(
            changed(
                "{\"name\":\"Aspirin\",\"blackTriangleSchemeFlag\":\"false\"}",
                "{\"name\":\"Aspirin\",\"blackTriangleSchemeFlag\":\"true\"}"))
        .isTrue();
  }

  @Test
  void aFieldTheModelDoesNotKnowAboutStillParticipates() {
    // Going through a DTO would drop it, and a dropped field can never be seen to change.
    assertThat(
            changed(
                "{\"name\":\"Aspirin\",\"someFutureRegisterField\":\"x\"}",
                "{\"name\":\"Aspirin\",\"someFutureRegisterField\":\"y\"}"))
        .isTrue();
  }

  @Test
  void identicalSnapshotsAreNotAChange() {
    String snapshot =
        "{\"name\":\"Aspirin\",\"licenceId\":\"12345\",\"products\":[{\"Name\":\"P\"}]}";
    assertThat(changed(snapshot, snapshot)).isFalse();
  }

  // ── Absent sides ────────────────────────────────────────────────────────────

  @Test
  void anEntryNeverRecordedCountsAsChangedSoItGetsProcessed() {
    assertThat(RegisterSnapshotComparator.hasChanged(null, json("{\"name\":\"Aspirin\"}")))
        .isTrue();
  }

  @Test
  void aMissingFreshEntryDoesNotTriggerARewriteFromDataWeDoNotHave() {
    assertThat(RegisterSnapshotComparator.hasChanged(json("{\"name\":\"Aspirin\"}"), null))
        .isFalse();
  }

  // ── The input must survive ──────────────────────────────────────────────────

  @Test
  void normalisingDoesNotModifyWhatItWasGiven() {
    JsonNode original =
        json("{\"name\":\"Aspirin\",\"effectiveDate\":\"2024-01-01\",\"blank\":\"\"}");
    String before = original.toString();

    RegisterSnapshotNormaliser.normalise(original);

    assertThat(original.toString()).isEqualTo(before);
  }
}
