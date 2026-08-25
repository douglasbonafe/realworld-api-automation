package dev.dsbon.realworld.restassured.support;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import dev.dsbon.realworld.restassured.spec.Specs;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Base class for every test in this module.
 *
 * <p>Spring boots once and caches the context for the whole run; REST Assured
 * supplies the HTTP client and the assertion DSL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class BaseApiTest {

  @Autowired protected RealWorldProperties properties;

  /** A specification with base URI and content type, but no credentials. */
  @Autowired protected RequestSpecification anonymousSpec;

  @BeforeEach
  void configureRestAssured() {
    Specs.enableFailureLogging(properties);
  }

  /** A specification that authenticates as the given token. */
  protected RequestSpecification authenticated(String token) {
    return Specs.authenticated(properties, token);
  }

  /**
   * Register a new account and return its details.
   *
   * <p><b>On api.realworld.show this changes the identity of every other caller.</b>
   * The sandbox keeps one global session: register A, then register B, and
   * {@code GET /user} with A's token returns B.
   *
   * <p>Consequences the suite is built around: no parallel execution (enforced in
   * the parent pom), any test needing its own identity registers at the start of
   * its own method, and scenarios requiring two simultaneous users are tagged
   * {@code multi-user} and excluded. See docs/contract-findings.md, finding #1.
   */
  protected AuthenticatedUser registerFreshUser() {
    String username = TestData.username();
    String email = TestData.email(username);
    String password = TestData.password();

    String token =
        given()
            .spec(anonymousSpec)
            .body(Map.of("user", Map.of("username", username, "email", email, "password", password)))
            .when()
            .post("/users")
            .then()
            .statusCode(201)
            .body("user.token", notNullValue())
            .extract()
            .path("user.token");

    return new AuthenticatedUser(username, email, password, token);
  }

  /** A registered account. Call {@link #authenticated(String)} with its token. */
  public record AuthenticatedUser(
      String username, String email, String password, String token) {}
}
