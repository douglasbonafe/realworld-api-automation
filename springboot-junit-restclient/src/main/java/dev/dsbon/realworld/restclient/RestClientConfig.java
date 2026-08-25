package dev.dsbon.realworld.restclient;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The {@link RestClient} bean every test shares.
 *
 * <p>Two settings here do the heavy lifting.
 */
@Configuration
public class RestClientConfig {

  @Bean
  public RestClient realWorldRestClient(RealWorldProperties properties) {
    HttpClient jdkClient =
        HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            // The API answers 3xx on some malformed paths. Following a redirect
            // would silently turn a routing bug into a passing test.
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkClient);
    requestFactory.setReadTimeout(properties.readTimeout());

    return RestClient.builder()
        .baseUrl(properties.baseUrl())
        .requestFactory(requestFactory)
        .defaultHeader("Accept", "application/json")
        /*
         * THE SINGLE MOST IMPORTANT LINE IN THIS MODULE.
         *
         * By default RestClient throws HttpClientErrorException on 4xx and
         * HttpServerErrorException on 5xx. That is right for application code —
         * and completely wrong for a test suite, where the status code is
         * usually the assertion. "GET /user without a token returns 401" is a
         * requirement, not an error.
         *
         * This handler matches every status and does nothing, so the response
         * always comes back as data and the test decides what it means. Getting
         * this wrong is why so many Spring-based API suites are full of
         * try/catch blocks that swallow the very thing being verified.
         */
        .defaultStatusHandler(status -> true, (request, response) -> {})
        .build();
  }
}
