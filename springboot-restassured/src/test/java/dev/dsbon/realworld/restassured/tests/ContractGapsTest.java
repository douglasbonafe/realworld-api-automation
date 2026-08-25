package dev.dsbon.realworld.restassured.tests;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThan;

import dev.dsbon.realworld.restassured.support.BaseApiTest;
import dev.dsbon.realworld.restassured.support.TestData;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Where api.realworld.show disagrees with its own published contract.
 *
 * <p>Excluded from the default run via {@code <excludedGroups>} in the module
 * pom. They are not flaky — they are reliable, and they fail. Excluding them
 * keeps a red build meaningful instead of a permanent block of known defects.
 *
 * <pre>
 *   mvn test -Dtest.excluded.groups= -Dgroups=contract-gaps
 * </pre>
 *
 * <p>Full write-up in docs/contract-findings.md.
 */
@DisplayName("Known contract gaps")
class ContractGapsTest extends BaseApiTest {

  @Test
  @Tag("contract-gaps")
  @DisplayName("GET /articles/{slug} should be readable without a token")
  void articleShouldBeReadableAnonymously() {
    var user = registerFreshUser();
    String slug = createArticle(user.token());

    given()
        .spec(anonymousSpec)
        .when()
        .get("/articles/{slug}", slug)
        .then()
        // The RealWorld spec and this service's own OpenAPI document both list
        // this operation as public (200/422, no 401 or 404). Observed: 404.
        .statusCode(200);
  }

  @Test
  @Tag("contract-gaps")
  @DisplayName("GET /articles/{slug}/comments should be readable without a token")
  void commentsShouldBeReadableAnonymously() {
    var user = registerFreshUser();
    String slug = createArticle(user.token());

    given()
        .spec(anonymousSpec)
        .when()
        .get("/articles/{slug}/comments", slug)
        .then()
        // Comments are a public read in the RealWorld spec. Observed: 404.
        .statusCode(200);
  }

  @Test
  @Tag("contract-gaps")
  @DisplayName("registering a duplicate username should return 409")
  void duplicateUsernameShouldConflict() {
    var user = registerFreshUser();

    given()
        .spec(anonymousSpec)
        .body(
            Map.of(
                "user",
                Map.of(
                    "username", user.username(),
                    "email", "other-" + user.email(),
                    "password", TestData.password())))
        .when()
        .post("/users")
        .then()
        // The OpenAPI document lists 409 for POST /users. This deployment
        // accepts the duplicate and returns 201 — usernames are not unique.
        .statusCode(409);
  }

  @Test
  @Tag("contract-gaps")
  @DisplayName("the ?author= filter should return that author's articles")
  void authorFilterShouldReturnResults() {
    var user = registerFreshUser();
    createArticle(user.token());

    given()
        .spec(authenticated(user.token()))
        .queryParam("author", user.username())
        .when()
        .get("/articles")
        .then()
        .statusCode(200)
        // Returns an empty page even though the author has articles.
        // ?tag= and ?favorited= behave the same way.
        .body("articles.size()", greaterThan(0));
  }

  @Test
  @Tag("contract-gaps")
  @Tag("multi-user")
  @DisplayName("a second user should be able to follow the first")
  void followingShouldWork() {
    var author = registerFreshUser();
    var follower = registerFreshUser();

    given()
        .spec(authenticated(follower.token()))
        .when()
        .post("/profiles/{username}/follow", author.username())
        .then()
        // Following needs two identities alive at once. This sandbox keeps ONE
        // global session, so by the time the follower exists the author's
        // profile is no longer resolvable and the call 404s.
        .statusCode(200);
  }

  private String createArticle(String token) {
    return given()
        .spec(authenticated(token))
        .body(TestData.newArticle(TestData.articleTitle()))
        .when()
        .post("/articles")
        .then()
        .statusCode(201)
        .extract()
        .path("article.slug");
  }
}
