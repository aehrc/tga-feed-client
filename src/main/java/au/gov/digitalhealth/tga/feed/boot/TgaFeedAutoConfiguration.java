package au.gov.digitalhealth.tga.feed.boot;

import au.gov.digitalhealth.tga.feed.TgaFeedClient;
import au.gov.digitalhealth.tga.feed.TgaRegisterClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Supplies the register clients to a Spring Boot application from {@code tga.feed.*} properties.
 *
 * <p>An application that wants to read the register should not have to write a class to construct
 * the client — that class carries no decision of its own beyond which property names to read, and
 * when each application picks its own names, identical settings drift apart unnoticed.
 *
 * <p>Both clients are {@link ConditionalOnMissingBean}, so an application that genuinely needs to
 * build one differently — a second register endpoint, a shared connection pool — still can, simply
 * by declaring its own bean.
 */
@AutoConfiguration
@EnableConfigurationProperties(TgaFeedSettings.class)
public class TgaFeedAutoConfiguration {

  /** The reactive client, for paged walks of the register. */
  @Bean
  @ConditionalOnMissingBean
  public TgaFeedClient tgaFeedClient(TgaFeedSettings settings, ObjectMapper objectMapper) {
    return new TgaFeedClient(settings.toProperties(), objectMapper);
  }

  /**
   * The blocking, cached client, for looking up one ARTG ID.
   *
   * <p>Shares the reactive client above rather than opening a second connection pool. Caching only
   * takes effect if the application enables caching and configures the {@link
   * TgaRegisterClient#CACHE_NAME} cache; without that, calls simply pass through.
   */
  @Bean
  @ConditionalOnMissingBean
  public TgaRegisterClient tgaRegisterClient(TgaFeedClient feedClient, TgaFeedSettings settings) {
    return new TgaRegisterClient(feedClient, settings.getResponseTimeout());
  }
}
