package au.gov.digitalhealth.tga.feed;

import au.gov.digitalhealth.tga.feed.model.RegisterEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Blocking, cached view of the register for applications that are not reactive.
 *
 * <p>{@link TgaFeedClient} is reactive because the register is paged and the feed processor walks
 * it concurrently. Most call sites are not: they want the entries for one ARTG ID, on the current
 * thread, and they want repeated asks within a run to cost one request. Every consumer that has
 * wanted that has written the same three things around the reactive client — a {@code block} with a
 * timeout, a cache, and a translation of the transport's exceptions — and written them differently.
 *
 * <p>Notably the differences were not cosmetic. One consumer's hand-rolled client applied no retry
 * at all and never reconciled the register's habit of answering a licence-ID query with unrelated
 * entries, so it silently processed the wrong product where the other did not. Sharing this class
 * means the timeout relationship below, the retry policy, and that reconciliation hold for everyone
 * rather than for whoever remembered.
 *
 * <p>Caching is opt-in and costs nothing to ignore: without {@code @EnableCaching} and a {@code
 * CacheManager} in the application, Spring does not proxy the bean and every call goes straight
 * through. Applications that do enable it configure the {@value #CACHE_NAME} cache's size and TTL
 * themselves, because how long register data may be held is an operational decision about a
 * particular deployment, not a property of the register.
 *
 * <p>Declare it as a {@code @Bean} rather than component-scanning this package, so the application
 * keeps control of its configuration.
 */
public class TgaRegisterClient {

  /** Cache holding register entries keyed by ARTG ID. Configured by the application. */
  public static final String CACHE_NAME = "tgaArtgEntries";

  /**
   * How much longer than a single response timeout the blocking wait allows.
   *
   * <p>The reactive client retries, so the wait has to outlast the whole retry sequence rather than
   * one attempt. Blocking for less would abandon requests the client was still legitimately
   * retrying — the caller would see a timeout while the work was in flight, and the register would
   * be asked again for something it was about to answer.
   */
  private static final int RETRY_HEADROOM_FACTOR = 4;

  private static final Logger log = LoggerFactory.getLogger(TgaRegisterClient.class);

  private final TgaFeedClient feedClient;
  private final Duration blockTimeout;

  public TgaRegisterClient(TgaFeedProperties properties, ObjectMapper mapper) {
    this(new TgaFeedClient(properties, mapper), properties.responseTimeout());
  }

  /**
   * Wraps a feed client the caller already has, for applications that share one transport across
   * both the paged walk and single lookups rather than opening a second connection pool.
   */
  public TgaRegisterClient(TgaFeedClient feedClient, Duration responseTimeout) {
    this.feedClient = feedClient;
    this.blockTimeout = responseTimeout.multipliedBy(RETRY_HEADROOM_FACTOR);
  }

  /**
   * Fetches every register entry carrying the given ARTG ID.
   *
   * <p>More than one entry is legitimate and not an error: a product registered in several
   * pack-size variants appears in the register once per registration.
   *
   * @return the matching entries, empty when the register holds none
   * @throws TgaFeedException if the register is unreachable or answers with an error
   */
  @Cacheable(value = CACHE_NAME, key = "#artgId")
  public List<RegisterEntry> findByArtgId(long artgId) {
    log.info("Fetching register entries for ARTG ID {}", artgId);

    try {
      List<RegisterEntry> matched = feedClient.fetchEntriesByLicenceId(artgId).block(blockTimeout);

      if (matched == null || matched.isEmpty()) {
        log.info("No register entry found for ARTG ID {}", artgId);
        return List.of();
      }

      log.info(
          "Register entries retrieved for ARTG ID {}: count={}, firstName={}",
          artgId,
          matched.size(),
          matched.get(0).getName());
      return matched;

    } catch (WebClientResponseException ex) {
      int status = ex.getStatusCode().value();
      log.error("Register returned HTTP {} for ARTG ID {}: {}", status, artgId, ex.getMessage());
      throw new TgaFeedException(
          "Register returned " + status + " for ARTG ID " + artgId, status, ex);
    } catch (TgaFeedException ex) {
      throw ex;
    } catch (Exception ex) {
      log.error("Error fetching register entry for ARTG ID {}: {}", artgId, ex.getMessage());
      throw new TgaFeedException("Failed to read the register for ARTG ID " + artgId, ex);
    }
  }
}
