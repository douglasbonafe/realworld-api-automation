package dev.dsbon.realworld.restassured.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration bound from {@code application.yml}.
 *
 * @param baseUrl root of the API, including the {@code /api} path segment
 * @param logOnFailure when true, dump the full request and response of any test
 *     whose assertions fail
 */
@ConfigurationProperties(prefix = "realworld")
public record RealWorldProperties(String baseUrl, Boolean logOnFailure) {

  public RealWorldProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "https://api.realworld.show/api";
    }
    // Boolean rather than boolean so that "absent" and "explicitly false" are
    // distinguishable, and the useful default (log the failure) wins on absent.
    if (logOnFailure == null) {
      logOnFailure = Boolean.TRUE;
    }
    // A trailing slash turns every path into a double slash, which some gateways
    // route differently. Normalising once removes a class of "works locally,
    // 404s in CI" report.
    if (baseUrl.endsWith("/")) {
      baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    }
  }
}
