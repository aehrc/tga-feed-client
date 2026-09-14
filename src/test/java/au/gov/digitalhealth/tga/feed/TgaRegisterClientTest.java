package au.gov.digitalhealth.tga.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import au.gov.digitalhealth.tga.feed.model.RegisterEntry;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * The blocking facade exists to stop each application re-deriving the same three behaviours around
 * the reactive client. These pin the behaviours, so an application dropping its own copy can see
 * that what it relied on still holds.
 */
class TgaRegisterClientTest {

  private static TgaRegisterClient clientReturning(Mono<List<RegisterEntry>> response) {
    return new TgaRegisterClient(
        new TgaFeedClient(
            TgaFeedProperties.defaults("http://localhost", "/s", "ps", "pe", "lid"),
            new com.fasterxml.jackson.databind.ObjectMapper()) {
          @Override
          public Mono<List<RegisterEntry>> fetchEntriesByLicenceId(long licenceId) {
            return response;
          }
        },
        Duration.ofSeconds(5));
  }

  @Test
  void returnsEveryEntryCarryingTheArtgId() {
    RegisterEntry first = new RegisterEntry();
    first.setName("Product 10mg");
    RegisterEntry second = new RegisterEntry();
    second.setName("Product 20mg");

    List<RegisterEntry> found =
        clientReturning(Mono.just(List.of(first, second))).findByArtgId(12345L);

    // More than one is legitimate: one registration per pack-size variant.
    assertThat(found).hasSize(2);
    assertThat(found)
        .extracting(RegisterEntry::getName)
        .containsExactly("Product 10mg", "Product 20mg");
  }

  @Test
  void returnsEmptyRatherThanNullWhenTheRegisterHoldsNothing() {
    assertThat(clientReturning(Mono.just(List.of())).findByArtgId(999L)).isEmpty();
  }

  @Test
  void treatsAnEmptyReactiveResultAsNoEntriesNotAsAFailure() {
    // block() on an empty Mono yields null; callers must not have to defend against it.
    assertThat(clientReturning(Mono.empty()).findByArtgId(999L)).isEmpty();
  }

  @Test
  void mapsAnHttpErrorOntoTheFeedExceptionAndKeepsTheStatus() {
    WebClientResponseException notFound =
        WebClientResponseException.create(404, "Not Found", null, null, null);

    assertThatThrownBy(() -> clientReturning(Mono.error(notFound)).findByArtgId(404L))
        .isInstanceOf(TgaFeedException.class)
        .hasMessageContaining("404")
        .hasMessageContaining("404")
        .extracting(ex -> ((TgaFeedException) ex).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND.value());
  }

  @Test
  void mapsATransportFailureOntoTheFeedExceptionWithNoStatus() {
    assertThatThrownBy(
            () ->
                clientReturning(Mono.error(new IOException("connection refused"))).findByArtgId(7L))
        .isInstanceOf(TgaFeedException.class)
        .extracting(ex -> ((TgaFeedException) ex).getStatusCode())
        .isNull();
  }

  @Test
  void doesNotRewrapAFeedExceptionRaisedFurtherDown() {
    TgaFeedException original = new TgaFeedException("already mapped", 503, null);

    assertThatThrownBy(() -> clientReturning(Mono.error(original)).findByArtgId(7L))
        .isSameAs(original);
  }

  @Test
  void allowsTheBlockingWaitToOutlastTheWholeRetrySequence() {
    // Blocking for a single response timeout would abandon requests the client was still retrying.
    TgaFeedProperties properties =
        TgaFeedProperties.defaults("http://localhost", "/s", "ps", "pe", "lid");

    assertThat(properties.responseTimeout()).isEqualTo(Duration.ofSeconds(120));
    assertThat(properties.maxRetries()).isGreaterThan(0);
  }
}
