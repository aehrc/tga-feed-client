package au.gov.digitalhealth.tga.feed.boot;

import au.gov.digitalhealth.tga.feed.TgaFeedProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for reading the TGA register, bound from {@code tga.feed.*}.
 *
 * <p>Exists so an application configures the register by setting properties rather than by writing
 * its own class to map property names onto {@link TgaFeedProperties}. Every consumer was writing
 * that class, each with different property names for the same settings — which is how two services
 * reading one register ended up with different timeouts and different retry policies without anyone
 * deciding they should differ.
 *
 * <p>Two kinds of setting live here, and they are deliberately treated differently.
 *
 * <p><strong>Where the register is</strong> — {@link #baseUrl} and {@link #searchUri} — has no
 * default and must be set. A library has no business deciding which host an application talks to:
 * that is the application's deployment, and a default would mean a deployment that forgot to
 * configure it silently reads the live register instead of failing. Absent values are reported at
 * startup, naming the property, rather than being quietly substituted.
 *
 * <p><strong>The register's wire contract</strong> — the query parameter names below — does carry
 * defaults, because it is the same for everyone talking to this register; it describes the
 * service's API, not a deployment. The timeouts, buffer size and retry policy likewise default to
 * values known to work, since they are tuning an application may reasonably not care about.
 */
@ConfigurationProperties(prefix = "tga.feed")
public class TgaFeedSettings {

  /** Base URL of the register. Required: {@code tga.feed.base-url}. */
  private String baseUrl;

  /** Path of the search endpoint. Required: {@code tga.feed.search-uri}. */
  private String searchUri;

  /** Query parameter naming the first record in a page window. */
  private String pageStartParam = "pagestart";

  /** Query parameter naming the last record in a page window. */
  private String pageEndParam = "pageend";

  /** Query parameter naming the licence (ARTG) ID. */
  private String licenceIdParam = "licenceid";

  private Duration connectTimeout = Duration.ofSeconds(120);

  private Duration responseTimeout = Duration.ofSeconds(120);

  /**
   * Maximum response size held in memory. The register answers a full page window in one document,
   * so this has to accommodate the largest page a deployment asks for.
   */
  private int maxInMemorySize = 1024 * 1024 * 1024;

  /** Retry attempts for transient failures. Zero disables retrying. */
  private int maxRetries = 3;

  private Duration retryBackoff = Duration.ofSeconds(60);

  /**
   * @throws IllegalStateException naming any required property that was not set. Failing here means
   *     a misconfigured deployment stops at startup rather than reading the wrong register for as
   *     long as it takes someone to notice.
   */
  public TgaFeedProperties toProperties() {
    List<String> missing = new ArrayList<>();
    if (baseUrl == null || baseUrl.isBlank()) {
      missing.add("tga.feed.base-url");
    }
    if (searchUri == null || searchUri.isBlank()) {
      missing.add("tga.feed.search-uri");
    }
    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "Cannot read the TGA register: required propert"
              + (missing.size() == 1 ? "y " : "ies ")
              + String.join(", ", missing)
              + " not set. These say which register to read and have no default, because a library"
              + " must not choose an application's environment.");
    }

    return new TgaFeedProperties(
        baseUrl,
        searchUri,
        pageStartParam,
        pageEndParam,
        licenceIdParam,
        connectTimeout,
        responseTimeout,
        maxInMemorySize,
        maxRetries,
        retryBackoff);
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getSearchUri() {
    return searchUri;
  }

  public void setSearchUri(String searchUri) {
    this.searchUri = searchUri;
  }

  public String getPageStartParam() {
    return pageStartParam;
  }

  public void setPageStartParam(String pageStartParam) {
    this.pageStartParam = pageStartParam;
  }

  public String getPageEndParam() {
    return pageEndParam;
  }

  public void setPageEndParam(String pageEndParam) {
    this.pageEndParam = pageEndParam;
  }

  public String getLicenceIdParam() {
    return licenceIdParam;
  }

  public void setLicenceIdParam(String licenceIdParam) {
    this.licenceIdParam = licenceIdParam;
  }

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public Duration getResponseTimeout() {
    return responseTimeout;
  }

  public void setResponseTimeout(Duration responseTimeout) {
    this.responseTimeout = responseTimeout;
  }

  public int getMaxInMemorySize() {
    return maxInMemorySize;
  }

  public void setMaxInMemorySize(int maxInMemorySize) {
    this.maxInMemorySize = maxInMemorySize;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(int maxRetries) {
    this.maxRetries = maxRetries;
  }

  public Duration getRetryBackoff() {
    return retryBackoff;
  }

  public void setRetryBackoff(Duration retryBackoff) {
    this.retryBackoff = retryBackoff;
  }
}
