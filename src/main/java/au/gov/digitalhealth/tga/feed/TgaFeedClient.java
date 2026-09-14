package au.gov.digitalhealth.tga.feed;

import au.gov.digitalhealth.tga.feed.model.RegisterEntry;
import au.gov.digitalhealth.tga.feed.model.RegisterSearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

/**
 * HTTP client for the TGA ARTG register feed, shared by the services that read it.
 *
 * <p>Both the nightly register scan and interactive single-ARTG-ID lookups previously carried their
 * own copy of this integration, which had drifted: one applied a retry policy on transport failures
 * and the other applied none. Sharing the transport means one answer to timeouts, buffer sizing and
 * retries regardless of which service is reading.
 *
 * <p>Most methods take the response type from the caller, so a service can keep its own
 * representation where it needs to. {@link #fetchEntriesByLicenceId} instead returns the shared
 * register model, since reconciling a licence-ID response is register behaviour rather than a
 * service's own concern.
 *
 * <p>Note that {@link au.gov.digitalhealth.tga.feed.RegisterSnapshot} — the form stored on tickets
 * — is deliberately separate from the wire model. Its field names are fixed by what is already
 * written on existing tickets, so it changes only with a migration.
 */
public class TgaFeedClient {

  private static final Logger log = LoggerFactory.getLogger(TgaFeedClient.class);

  /** Cause chains are shallow in practice; this only has to be deep enough to be safe. */
  private static final int MAX_CAUSE_DEPTH = 20;

  private final WebClient webClient;
  private final ObjectMapper mapper;
  private final TgaFeedProperties properties;

  public TgaFeedClient(TgaFeedProperties properties, ObjectMapper mapper) {
    this.properties = properties;
    this.mapper = mapper;
    this.webClient =
        WebClient.builder()
            .baseUrl(properties.baseUrl())
            .clientConnector(
                new ReactorClientHttpConnector(
                    HttpClient.create()
                        .option(
                            ChannelOption.CONNECT_TIMEOUT_MILLIS,
                            (int) properties.connectTimeout().toMillis())
                        .responseTimeout(properties.responseTimeout())))
            .codecs(
                configurer ->
                    configurer.defaultCodecs().maxInMemorySize(properties.maxInMemorySize()))
            .build();
  }

  /**
   * Fetches one page window of the register.
   *
   * @param pageStart first record in the window
   * @param pageEnd last record in the window, inclusive
   * @param responseType the caller's own type for the search response
   */
  public <T> Mono<T> fetchPage(int pageStart, int pageEnd, Class<T> responseType) {
    return fetch(pageQuery(pageStart, pageEnd), responseType);
  }

  /**
   * Fetches the register entry for a single ARTG licence ID.
   *
   * @param responseType the caller's own type for the search response
   */
  public <T> Mono<T> fetchByLicenceId(long licenceId, Class<T> responseType) {
    return fetch(
        properties.searchUri() + "?" + properties.licenceIdParam() + "=" + licenceId, responseType);
  }

  /**
   * Fetches the register entries for one licence ID, keeping only those that actually carry it.
   *
   * <p>The register does not reliably return only the entry asked for — a licence-ID query can come
   * back with unrelated entries alongside it. Every caller therefore has to filter the response,
   * and every caller that forgets silently processes the wrong product. Doing it here means the
   * quirk is handled once, in the place that knows about it.
   *
   * @return the matching entries, or an empty list when the register holds none. Multiple entries
   *     are legitimate: a product registered in several pack-size variants appears more than once.
   */
  public Mono<List<RegisterEntry>> fetchEntriesByLicenceId(long licenceId) {
    return fetchByLicenceId(licenceId, RegisterSearchResult.class)
        .map(
            result -> {
              if (result == null || result.getResults() == null) {
                return List.<RegisterEntry>of();
              }
              String requested = Long.toString(licenceId);
              List<RegisterEntry> matching =
                  result.getResults().stream()
                      .filter(entry -> requested.equals(entry.getLicenceId()))
                      .toList();
              if (matching.isEmpty() && !result.getResults().isEmpty()) {
                log.warn(
                    "Register returned {} entr(y/ies) for licence ID {} but none carried that ID —"
                        + " treating as no result",
                    result.getResults().size(),
                    licenceId);
              }
              return matching;
            })
        .defaultIfEmpty(List.of());
  }

  /**
   * Splits a record range into page windows of at most {@code pageSize}.
   *
   * <p>Shared because both the window arithmetic and its inclusive-end convention are easy to get
   * subtly wrong, and a caller that miscomputes windows silently skips register entries.
   *
   * @return one {@code [start, end]} pair per window, ends inclusive
   */
  public List<int[]> pageWindows(int pageStart, int pageEnd, int pageSize) {
    if (pageSize <= 0) {
      throw new IllegalArgumentException("pageSize must be positive, got " + pageSize);
    }
    List<int[]> windows = new ArrayList<>();
    for (int i = pageStart; i <= pageEnd; i += pageSize) {
      windows.add(new int[] {i, i + pageSize - 1});
    }
    return windows;
  }

  /** The query string this client would issue for a page window. Exposed for logging and tests. */
  public String pageQuery(int pageStart, int pageEnd) {
    return properties.searchUri()
        + "?"
        + properties.pageStartParam()
        + "="
        + pageStart
        + "&"
        + properties.pageEndParam()
        + "="
        + pageEnd;
  }

  /**
   * Issues the request, logs its duration, and deserialises the body.
   *
   * <p>Retries only transport-level failures (connection problems and timeouts). A malformed body
   * is not retried — re-requesting will not change it — and neither is an HTTP error status, which
   * would need the caller's own judgement.
   */
  private <T> Mono<T> fetch(String uri, Class<T> responseType) {
    Instant start = Instant.now();
    return webClient
        .get()
        .uri(uri)
        .retrieve()
        .bodyToMono(String.class)
        .retryWhen(
            Retry.backoff(properties.maxRetries(), properties.retryBackoff())
                .filter(TgaFeedClient::isTransient)
                .doBeforeRetry(
                    signal ->
                        log.warn(
                            "Retrying TGA request {} after transport failure (attempt {}): {}",
                            uri,
                            signal.totalRetries() + 1,
                            signal.failure().getMessage())))
        .doOnNext(
            body ->
                log.info(
                    "Fetched TGA data from {} in {}s ({} bytes)",
                    uri,
                    Duration.between(start, Instant.now()).getSeconds(),
                    body.length()))
        .doOnError(
            error ->
                log.error(
                    "Failed fetching TGA data from {} after {}s: {}",
                    uri,
                    Duration.between(start, Instant.now()).getSeconds(),
                    error.getMessage()))
        .flatMap(
            body -> {
              try {
                return Mono.just(mapper.readValue(body, responseType));
              } catch (Exception e) {
                return Mono.error(e);
              }
            });
  }

  /**
   * True for failures a retry could plausibly resolve.
   *
   * <p>Walks the cause chain to a bounded depth. Cause chains can be cyclic — {@code
   * a.initCause(b)} after {@code b} was constructed with {@code a} as its cause produces a loop —
   * so the bound is what guarantees termination, rather than a self-reference check that a
   * two-element cycle would slip past.
   */
  static boolean isTransient(Throwable error) {
    Throwable t = error;
    for (int depth = 0; t != null && depth < MAX_CAUSE_DEPTH; depth++, t = t.getCause()) {
      if (t instanceof TimeoutException || t instanceof java.io.IOException) {
        return true;
      }
    }
    return false;
  }
}
