package dev.dsbon.realworld.restclient;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration bound from {@code application.yml}, overridable by system
 * property, environment variable or {@code @TestPropertySource}.
 *
 * <p>Typed configuration is one of the concrete wins of the Spring module: the
 * plain-Java client reads {@code System.getProperty} by hand, and REST Assured
 * takes a static string. Here the value is validated at context startup, has a
 * default, appears in IDE autocompletion via the configuration processor, and
 * can be overridden per test class without touching code.
 *
 * @param baseUrl root of the API, including the {@code /api} path segment
 * @param connectTimeout how long to wait for a connection
 * @param readTimeout how long to wait for a response
 */
@ConfigurationProperties(prefix = "realworld")
public record RealWorldProperties(
    String baseUrl, Duration connectTimeout, Duration readTimeout) {

  public RealWorldProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "https://api.realworld.show/api";
    }
    // Trailing slashes turn every path into a double slash, which some gateways
    // treat as a different route. Normalising once here removes a whole class of
    // "works locally, 404s in CI" report.
    if (baseUrl.endsWith("/")) {
      baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    }
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(10);
    }
    if (readTimeout == null) {
      readTimeout = Duration.ofSeconds(30);
    }
  }
}
