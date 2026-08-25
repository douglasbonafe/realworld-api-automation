package dev.dsbon.realworld.restassured.spec;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import dev.dsbon.realworld.restassured.support.RealWorldProperties;

/**
 * The shared REST Assured specifications, built once as Spring beans.
 *
 * <p>Specifications are REST Assured's answer to the question every API suite
 * eventually asks: "where does the base URL, the content type and the logging
 * live?" Building them here means a test reads as
 *
 * <pre>{@code
 * given().spec(anonymous).when().get("/tags").then().spec(ok)
 * }</pre>
 *
 * with no repeated setup and one place to change when the environment moves.
 */
@Configuration
public class Specs {

  /** No {@code Authorization} header — for the tests that assert rejection. */
  @Bean
  public RequestSpecification anonymousSpec(RealWorldProperties properties) {
    return baseSpec(properties).build();
  }

  /**
   * A specification that authenticates as the given token.
   *
   * <p>Not a bean: the token is per test, so this is a factory the tests call.
   * Everything except the header comes from the same base, so an authenticated
   * request cannot drift from an anonymous one.
   */
  public static RequestSpecification authenticated(
      RealWorldProperties properties, String token) {
    // NOT "Bearer". RealWorld's scheme is literally `Token <value>`, and the
    // value this deployment issues is not a JWT despite its own OpenAPI
    // description claiming otherwise. See docs/contract-findings.md.
    return baseSpec(properties).addHeader("Authorization", "Token " + token).build();
  }

  /**
   * A reusable "successful JSON response" expectation.
   *
   * <p>Response specifications are the half of the feature most suites forget.
   * Asserting the content type on every success is a one-line habit that catches
   * a server switching to {@code text/html} on an error page — which otherwise
   * shows up as a bizarre JSON parse failure three assertions later.
   */
  @Bean
  public ResponseSpecification okJson() {
    return new ResponseSpecBuilder()
        .expectStatusCode(200)
        .expectContentType(ContentType.JSON)
        .build();
  }

  @Bean
  public ResponseSpecification createdJson() {
    return new ResponseSpecBuilder()
        .expectStatusCode(201)
        .expectContentType(ContentType.JSON)
        .build();
  }

  private static RequestSpecBuilder baseSpec(RealWorldProperties properties) {
    return new RequestSpecBuilder()
        .setBaseUri(properties.baseUrl())
        .setContentType(ContentType.JSON)
        .setAccept(ContentType.JSON);
  }

  /**
   * Turn on "print everything, but only when a test fails".
   *
   * <p>This is the single highest-value REST Assured setting for CI, and it is a
   * global switch rather than a filter — {@link RequestLoggingFilter} and {@link
   * ResponseLoggingFilter} log unconditionally, which buries a passing run in
   * noise.
   *
   * <p>With this, a green run prints nothing and a red one prints the full
   * request and response — headers, body, everything — immediately above the
   * assertion error. No re-running with logging switched on, no guessing at what
   * the server actually sent.
   *
   * <p>Called from the test base class rather than a bean initializer, because
   * it mutates REST Assured's global state and belongs where the test lifecycle
   * is visible.
   */
  public static void enableFailureLogging(RealWorldProperties properties) {
    if (Boolean.TRUE.equals(properties.logOnFailure())) {
      RestAssured.enableLoggingOfRequestAndResponseIfValidationFails(LogDetail.ALL);
    }
  }
}
