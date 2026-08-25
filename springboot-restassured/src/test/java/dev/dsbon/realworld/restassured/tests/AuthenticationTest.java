package dev.dsbon.realworld.restassured.tests;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import dev.dsbon.realworld.restassured.support.BaseApiTest;
import dev.dsbon.realworld.restassured.support.TestData;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import io.restassured.specification.ResponseSpecification;

@DisplayName("Authentication")
class AuthenticationTest extends BaseApiTest {

  @Autowired ResponseSpecification okJson;
  @Autowired ResponseSpecification createdJson;

  @Test
  @Tag("smoke")
  @DisplayName("registering returns 201, a token, and a body matching the user schema")
  void registersANewUser() {
    String username = TestData.username();
    String email = TestData.email(username);

    given()
        .spec(anonymousSpec)
        .body(
            Map.of(
                "user",
                Map.of("username", username, "email", email, "password", TestData.password())))
        .when()
        .post("/users")
        .then()
        .spec(createdJson)
        // One line replaces a dozen field assertions: types, required keys and
        // nullability all checked at once. This is REST Assured's clearest
        // advantage over the other two modules.
        .body(matchesJsonSchemaInClasspath("schemas/user-response.json"))
        .body("user.username", equalTo(username))
        .body("user.email", equalTo(email))
        .body("user.token", notNullValue())
        // A fresh account has no profile content yet.
        .body("user.bio", nullValue())
        .body("user.image", nullValue());
  }

  @Test
  @Tag("contract")
  @DisplayName("a registration missing required fields returns 422 with field errors")
  void rejectsIncompleteRegistration() {
    given()
        .spec(anonymousSpec)
        // A map can express a deliberately incomplete payload that a typed
        // record could not represent at all — the reason this module builds
        // bodies from maps.
        .body(Map.of("user", Map.of("username", TestData.username())))
        .when()
        .post("/users")
        .then()
        .statusCode(422)
        .body("errors", hasKey("email"))
        .body("errors", hasKey("password"))
        .body(matchesJsonSchemaInClasspath("schemas/errors-response.json"));
  }

  @Test
  @Tag("smoke")
  @DisplayName("valid credentials return 200 and a token")
  void logsInWithValidCredentials() {
    var user = registerFreshUser();

    given()
        .spec(anonymousSpec)
        .body(TestData.login(user.email(), user.password()))
        .when()
        .post("/users/login")
        .then()
        .spec(okJson)
        .body("user.token", notNullValue());
  }

  @Test
  @Tag("contract")
  @DisplayName("wrong credentials return 401 with a generic message")
  void rejectsWrongCredentials() {
    var user = registerFreshUser();

    given()
        .spec(anonymousSpec)
        .body(TestData.login(user.email(), "not-the-password"))
        .when()
        .post("/users/login")
        .then()
        .statusCode(401)
        // One generic key. The API does not distinguish "no such account" from
        // "wrong password", which is the correct enumeration defence — and the
        // `not(hasKey(...))` half is what would catch a regression that starts
        // leaking the difference.
        .body("errors", hasKey("credentials"))
        .body("errors", not(hasKey("email")));
  }

  @Test
  @Tag("smoke")
  @DisplayName("GET /user without a token returns 401")
  void rejectsAnonymousCurrentUser() {
    given()
        .spec(anonymousSpec)
        .when()
        .get("/user")
        .then()
        .statusCode(401)
        .body("errors", hasKey("token"));
  }

  @Test
  @Tag("contract")
  @DisplayName("a malformed token is rejected like no token at all")
  void rejectsGarbageToken() {
    given()
        .spec(authenticated("token_not_a_real_token"))
        .when()
        .get("/user")
        .then()
        .statusCode(401);
  }

  @Test
  @Tag("smoke")
  @DisplayName("GET /user with a token returns the registered account")
  void returnsTheCurrentUser() {
    var user = registerFreshUser();

    given()
        .spec(authenticated(user.token()))
        .when()
        .get("/user")
        .then()
        .spec(okJson)
        .body("user.username", equalTo(user.username()));
  }

  @Test
  @Tag("contract")
  @DisplayName("PUT /user updates only the fields that were sent")
  void updatesTheCurrentUser() {
    var user = registerFreshUser();
    String bio = "Staff QA engineer — " + System.nanoTime();

    given()
        .spec(authenticated(user.token()))
        .body(Map.of("user", Map.of("bio", bio)))
        .when()
        .put("/user")
        .then()
        .spec(okJson)
        .body("user.bio", equalTo(bio))
        // The half of the assertion people forget: a partial update must not
        // clobber what it did not mention.
        .body("user.username", equalTo(user.username()))
        .body("user.email", equalTo(user.email()));
  }
}
