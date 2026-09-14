package au.gov.digitalhealth.tga.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

/** Covers the query building, page-window arithmetic and retry classification shared by callers. */
class TgaFeedClientTest {

  private final TgaFeedClient client =
      new TgaFeedClient(
          TgaFeedProperties.defaults(
              "https://data.tga.gov.au",
              "/ARTGSearch/ARTGWebService.svc/json/ARTGValueSearch/",
              "pagestart",
              "pageend",
              "licenceid"),
          new ObjectMapper());

  @Test
  void pageQuery_usesConfiguredParameterNames() {
    assertThat(client.pageQuery(1, 100))
        .isEqualTo("/ARTGSearch/ARTGWebService.svc/json/ARTGValueSearch/?pagestart=1&pageend=100");
  }

  @Test
  void pageWindows_coverRangeWithInclusiveEnds() {
    List<int[]> windows = client.pageWindows(1, 250, 100);

    assertThat(windows).hasSize(3);
    assertThat(windows.get(0)).containsExactly(1, 100);
    assertThat(windows.get(1)).containsExactly(101, 200);
    // Final window may overshoot the requested end — the feed simply returns fewer records.
    assertThat(windows.get(2)).containsExactly(201, 300);
  }

  @Test
  void pageWindows_singleWindowWhenRangeFitsInOnePage() {
    assertThat(client.pageWindows(1, 50, 100)).hasSize(1);
  }

  @Test
  void pageWindows_rejectsNonPositivePageSize() {
    assertThatThrownBy(() -> client.pageWindows(1, 100, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pageSize must be positive");
  }

  @Test
  void transportFailuresAreRetryable() {
    assertThat(TgaFeedClient.isTransient(new SocketTimeoutException("read timed out"))).isTrue();
    assertThat(TgaFeedClient.isTransient(new IOException("connection reset"))).isTrue();
    assertThat(TgaFeedClient.isTransient(new TimeoutException("no response"))).isTrue();
    // Wrapped causes still count — WebClient wraps transport errors.
    assertThat(TgaFeedClient.isTransient(new RuntimeException(new IOException("reset")))).isTrue();
  }

  @Test
  void malformedResponsesAreNotRetryable() {
    // Re-requesting will not turn invalid JSON into valid JSON.
    assertThat(TgaFeedClient.isTransient(new IllegalArgumentException("bad json"))).isFalse();
  }

  @Test
  void cyclicCauseChainTerminates() {
    // Java forbids self-causation, but a two-element cycle is constructible and would hang an
    // unbounded walk of the cause chain.
    RuntimeException first = new RuntimeException("first");
    RuntimeException second = new RuntimeException("second", first);
    first.initCause(second);

    assertThat(TgaFeedClient.isTransient(first)).isFalse();
  }

  @Test
  void transientCauseIsFoundEvenInsideACyclicChain() {
    RuntimeException outer = new RuntimeException("outer");
    RuntimeException inner = new RuntimeException("inner", new IOException("reset"));
    outer.initCause(inner);

    assertThat(TgaFeedClient.isTransient(outer)).isTrue();
  }
}
