package au.gov.digitalhealth.tga.feed;

import java.time.Duration;

/**
 * Connection and query settings for the TGA register feed.
 *
 * <p>Held as a plain value rather than a Spring {@code @ConfigurationProperties} class so each
 * consuming service keeps ownership of its own property names and defaults, and this module stays
 * usable without Spring Boot configuration binding.
 *
 * @param baseUrl feed host, e.g. {@code https://data.tga.gov.au}
 * @param searchUri search service path beneath {@link #baseUrl}
 * @param pageStartParam query parameter naming the first record in a page window
 * @param pageEndParam query parameter naming the last record in a page window
 * @param licenceIdParam query parameter naming a single ARTG licence ID
 * @param connectTimeout TCP connect timeout
 * @param responseTimeout time allowed for the feed to respond; register pages are large and slow,
 *     so this is generous by design
 * @param maxInMemorySize response buffer ceiling — a full register page exceeds WebClient's default
 * @param maxRetries attempts after the first failure, applied only to transport-level errors
 * @param retryBackoff initial backoff between retries, doubling per attempt
 */
public record TgaFeedProperties(
    String baseUrl,
    String searchUri,
    String pageStartParam,
    String pageEndParam,
    String licenceIdParam,
    Duration connectTimeout,
    Duration responseTimeout,
    int maxInMemorySize,
    int maxRetries,
    Duration retryBackoff) {

  /**
   * Settings matching the feed processor's long-standing configuration, which the interactive
   * lookup path did not previously share — notably the retry policy, which it had none of.
   */
  public static TgaFeedProperties defaults(
      String baseUrl,
      String searchUri,
      String pageStartParam,
      String pageEndParam,
      String licenceIdParam) {
    return new TgaFeedProperties(
        baseUrl,
        searchUri,
        pageStartParam,
        pageEndParam,
        licenceIdParam,
        Duration.ofSeconds(120),
        Duration.ofSeconds(120),
        1024 * 1024 * 1024,
        3,
        Duration.ofSeconds(60));
  }
}
