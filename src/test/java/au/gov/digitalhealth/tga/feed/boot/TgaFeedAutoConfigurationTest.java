package au.gov.digitalhealth.tga.feed.boot;

import static org.assertj.core.api.Assertions.assertThat;

import au.gov.digitalhealth.tga.feed.TgaFeedClient;
import au.gov.digitalhealth.tga.feed.TgaFeedProperties;
import au.gov.digitalhealth.tga.feed.TgaRegisterClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The settings moved out of each application and into this library, so every setting an application
 * used to control by writing its own configuration class must still be controllable by property.
 *
 * <p>These assert that, one setting at a time. A setting that silently stops being honoured is the
 * expensive failure here: nothing errors, the register is simply read with the wrong timeout, the
 * wrong retry policy, or against the wrong host.
 */
class TgaFeedAutoConfigurationTest {

  /** Where the register is has no default, so every case that expects success must supply it. */
  private static final String[] REQUIRED_LOCATION = {
    "tga.feed.base-url=https://data.tga.gov.au",
    "tga.feed.search-uri=/ARTGSearch/ARTGWebService.svc/json/ARTGValueSearch/"
  };

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(TgaFeedAutoConfiguration.class))
          .withUserConfiguration(MapperConfig.class)
          .withPropertyValues(REQUIRED_LOCATION);

  @Configuration
  static class MapperConfig {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }

  @Test
  void refusesToStartWithoutBeingToldWhichRegisterToRead() {
    // A default here would let a deployment that forgot to configure the register silently read the
    // live one. The message has to name the property, or whoever hits this cannot act on it.
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TgaFeedAutoConfiguration.class))
        .withUserConfiguration(MapperConfig.class)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("tga.feed.base-url")
                    .hasMessageContaining("tga.feed.search-uri"));
  }

  @Test
  void namesOnlyTheRequiredPropertyThatIsActuallyMissing() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TgaFeedAutoConfiguration.class))
        .withUserConfiguration(MapperConfig.class)
        .withPropertyValues("tga.feed.base-url=https://data.tga.gov.au")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("tga.feed.search-uri")
                    .hasMessageNotContaining("tga.feed.base-url"));
  }

  @Test
  void suppliesBothClientsOnceItKnowsWhichRegisterToRead() {
    runner.run(
        context ->
            assertThat(context)
                .hasSingleBean(TgaFeedClient.class)
                .hasSingleBean(TgaRegisterClient.class));
  }

  @Test
  void theWireContractAndTuningDefaultToValuesKnownToWork() {
    runner.run(
        context -> {
          TgaFeedProperties p = context.getBean(TgaFeedSettings.class).toProperties();
          assertThat(p.pageStartParam()).isEqualTo("pagestart");
          assertThat(p.pageEndParam()).isEqualTo("pageend");
          assertThat(p.licenceIdParam()).isEqualTo("licenceid");
          assertThat(p.connectTimeout()).isEqualTo(Duration.ofSeconds(120));
          assertThat(p.responseTimeout()).isEqualTo(Duration.ofSeconds(120));
          assertThat(p.maxInMemorySize()).isEqualTo(1024 * 1024 * 1024);
          assertThat(p.maxRetries()).isEqualTo(3);
          assertThat(p.retryBackoff()).isEqualTo(Duration.ofSeconds(60));
        });
  }

  @Test
  void everySettingCanBeOverriddenByTheApplication() {
    runner
        .withPropertyValues(
            "tga.feed.base-url=https://example.invalid",
            "tga.feed.search-uri=/other/search",
            "tga.feed.page-start-param=from",
            "tga.feed.page-end-param=to",
            "tga.feed.licence-id-param=artgid",
            "tga.feed.connect-timeout=5s",
            "tga.feed.response-timeout=7s",
            "tga.feed.max-in-memory-size=2048",
            "tga.feed.max-retries=9",
            "tga.feed.retry-backoff=11s")
        .run(
            context -> {
              TgaFeedProperties p = context.getBean(TgaFeedSettings.class).toProperties();
              assertThat(p.baseUrl()).isEqualTo("https://example.invalid");
              assertThat(p.searchUri()).isEqualTo("/other/search");
              assertThat(p.pageStartParam()).isEqualTo("from");
              assertThat(p.pageEndParam()).isEqualTo("to");
              assertThat(p.licenceIdParam()).isEqualTo("artgid");
              assertThat(p.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
              assertThat(p.responseTimeout()).isEqualTo(Duration.ofSeconds(7));
              assertThat(p.maxInMemorySize()).isEqualTo(2048);
              assertThat(p.maxRetries()).isEqualTo(9);
              assertThat(p.retryBackoff()).isEqualTo(Duration.ofSeconds(11));
            });
  }

  @Test
  void timeoutsAlsoAcceptPlainMilliseconds() {
    // An application migrating from its own millisecond-valued property should not have to convert.
    runner
        .withPropertyValues("tga.feed.connect-timeout=30000ms", "tga.feed.response-timeout=60000ms")
        .run(
            context -> {
              TgaFeedSettings settings = context.getBean(TgaFeedSettings.class);
              assertThat(settings.getConnectTimeout()).isEqualTo(Duration.ofSeconds(30));
              assertThat(settings.getResponseTimeout()).isEqualTo(Duration.ofSeconds(60));
            });
  }

  @Test
  void unrelatedKeysUnderTheSamePrefixDoNotBreakBinding() {
    // The feed processor keeps its own scan window under tga.feed.page.*. Those are its properties,
    // not this library's, and binding must ignore them rather than fail on an unknown field.
    runner
        .withPropertyValues(
            "tga.feed.page.start=1", "tga.feed.page.end=5000", "tga.feed.page.size=1000")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(TgaFeedSettings.class).getBaseUrl())
                  .isEqualTo("https://data.tga.gov.au");
            });
  }

  @Test
  void anApplicationCanStillSupplyItsOwnClient() {
    TgaFeedClient own =
        new TgaFeedClient(
            TgaFeedProperties.defaults("http://own", "/s", "ps", "pe", "lid"), new ObjectMapper());

    runner
        .withBean(TgaFeedClient.class, () -> own)
        .run(context -> assertThat(context.getBean(TgaFeedClient.class)).isSameAs(own));
  }
}
