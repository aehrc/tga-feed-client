package au.gov.digitalhealth.tga.feed;

/**
 * Thrown when the register cannot be read.
 *
 * <p>Exists so callers have one failure type to catch. Without it each consumer has to reason about
 * the transport's own exceptions — {@code WebClientResponseException} for an HTTP status, a timeout
 * for a blocking wait that expired, whatever the retry wrapper throws once attempts are exhausted —
 * and they reason about them differently. The register being unreachable is one condition as far as
 * a caller is concerned, so it gets one type.
 *
 * <p>Applications are still expected to map this onto their own error representation; what they no
 * longer have to do is enumerate the transport's failure modes to get there.
 */
public class TgaFeedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Set when the failure carried an HTTP status, otherwise {@code null}. */
  private final Integer statusCode;

  public TgaFeedException(String message, Throwable cause) {
    this(message, null, cause);
  }

  public TgaFeedException(String message, Integer statusCode, Throwable cause) {
    super(message, cause);
    this.statusCode = statusCode;
  }

  /**
   * @return the HTTP status the register responded with, or {@code null} when the failure happened
   *     before a response was received (connection refused, timeout, exhausted retries)
   */
  public Integer getStatusCode() {
    return statusCode;
  }
}
