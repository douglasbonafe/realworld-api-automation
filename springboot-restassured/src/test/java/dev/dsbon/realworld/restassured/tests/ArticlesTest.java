package dev.dsbon.realworld.restassured.tests;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

import dev.dsbon.realworld.restassured.support.BaseApiTest;
import dev.dsbon.realworld.restassured.support.TestData;
import io.restassured.specification.ResponseSpecification;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Articles")
class ArticlesTest extends BaseApiTest {

  @Autowired ResponseSpecification okJson;
  @Autowired ResponseSpecification createdJson;

  @Test
  @Tag("smoke")
  @DisplayName("creating an article returns 201 and the derived slug")
  void createsAnArticle() {
    var user = registerFreshUser();
    String title = TestData.articleTitle();

    given()
        .spec(authenticated(user.token()))
        .body(TestData.newArticle(title))
        .when()
        .post("/articles")
        .then()
        .spec(createdJson)
        .body(matchesJsonSchemaInClasspath("schemas/article-response.json"))
        .body("article.title", equalTo(title))
        // The slug is derived, not echoed — see TestData.slugFor for the rule,
        // which was reverse-engineered from the live service.
        .body("article.slug", equalTo(TestData.slugFor(title)))
        // hasItems reads better than an exact list here: the assertion is "my
        // tags survived", not "the server returned them in this order".
        .body("article.tagList", hasItems("qa", "automation"))
        .body("article.favorited", equalTo(false))
        .body("article.favoritesCount", equalTo(0))
        .body("article.author.username", equalTo(user.username()));
  }

  @Test
  @Tag("smoke")
  @DisplayName("creating an article without a token returns 401")
  void rejectsAnonymousCreation() {
    given()
        .spec(anonymousSpec)
        .body(TestData.newArticle(TestData.articleTitle()))
        .when()
        .post("/articles")
        .then()
        .statusCode(401)
        .body("errors", hasKey("token"));
  }

  @Test
  @Tag("contract")
  @DisplayName("an article can be read back by its slug")
  void readsAnArticleBySlug() {
    var user = registerFreshUser();
    String title = TestData.articleTitle();
    String slug = createArticle(user.token(), title);

    // Authenticated on purpose: this deployment resolves reads against the
    // current session and 404s an anonymous GET of a real article. The anonymous
    // case is asserted separately in ContractGapsTest.
    given()
        .spec(authenticated(user.token()))
        .when()
        .get("/articles/{slug}", slug)
        .then()
        .spec(okJson)
        .body("article.title", equalTo(title));
  }

  @Test
  @Tag("contract")
  @DisplayName("an unknown slug returns 404 with an errors body")
  void returns404ForUnknownSlug() {
    var user = registerFreshUser();

    given()
        .spec(authenticated(user.token()))
        .when()
        .get("/articles/{slug}", "no-such-slug-" + System.nanoTime())
        .then()
        .statusCode(404)
        .body("errors", hasKey("article"))
        .body(matchesJsonSchemaInClasspath("schemas/errors-response.json"));
  }

  @Test
  @Tag("contract")
  @DisplayName("updating an article changes only the fields sent")
  void updatesAnArticle() {
    var user = registerFreshUser();
    String title = TestData.articleTitle();
    String slug = createArticle(user.token(), title);

    given()
        .spec(authenticated(user.token()))
        .body(Map.of("article", Map.of("description", "updated description")))
        .when()
        .put("/articles/{slug}", slug)
        .then()
        .spec(okJson)
        .body("article.description", equalTo("updated description"))
        // The untouched field must survive the partial update.
        .body("article.title", equalTo(title));
  }

  @Test
  @Tag("smoke")
  @DisplayName("deleting an article returns 204 and the article is gone")
  void deletesAnArticle() {
    var user = registerFreshUser();
    String slug = createArticle(user.token(), TestData.articleTitle());

    given()
        .spec(authenticated(user.token()))
        .when()
        .delete("/articles/{slug}", slug)
        .then()
        .statusCode(204);

    // A delete that returns 204 but leaves the resource readable is a bug that
    // only a follow-up read catches.
    given()
        .spec(authenticated(user.token()))
        .when()
        .get("/articles/{slug}", slug)
        .then()
        .statusCode(404);
  }

  @Test
  @Tag("contract")
  @DisplayName("favouriting toggles the flag and the counter")
  void favouritesAndUnfavourites() {
    var user = registerFreshUser();
    String slug = createArticle(user.token(), TestData.articleTitle());

    given()
        .spec(authenticated(user.token()))
        .when()
        .post("/articles/{slug}/favorite", slug)
        .then()
        .spec(okJson)
        .body("article.favorited", equalTo(true))
        .body("article.favoritesCount", equalTo(1));

    given()
        .spec(authenticated(user.token()))
        .when()
        .delete("/articles/{slug}/favorite", slug)
        .then()
        .spec(okJson)
        .body("article.favorited", equalTo(false))
        .body("article.favoritesCount", equalTo(0));
  }

  @Test
  @Tag("smoke")
  @DisplayName("listing articles returns a page and a total count")
  void listsArticles() {
    given()
        .spec(anonymousSpec)
        .queryParam("limit", 3)
        .when()
        .get("/articles")
        .then()
        .spec(okJson)
        .body("articles", notNullValue())
        // Groovy-style GPath: `articles.size()` is evaluated inside the JSON
        // document, no deserialization required. This is REST Assured's other
        // signature capability.
        .body("articles.size()", lessThanOrEqualTo(3))
        .body("articlesCount", greaterThanOrEqualTo(0));
  }

  @Test
  @Tag("smoke")
  @DisplayName("the personal feed requires authentication")
  void feedRequiresAuthentication() {
    given().spec(anonymousSpec).when().get("/articles/feed").then().statusCode(401);

    var user = registerFreshUser();

    given()
        .spec(authenticated(user.token()))
        .queryParam("limit", 5)
        .when()
        .get("/articles/feed")
        .then()
        .spec(okJson)
        // A brand-new account follows nobody, so an empty feed is correct. The
        // assertion is on the shape, not the content.
        .body("articles", notNullValue());
  }

  private String createArticle(String token, String title) {
    return given()
        .spec(authenticated(token))
        .body(TestData.newArticle(title))
        .when()
        .post("/articles")
        .then()
        .statusCode(201)
        .extract()
        .path("article.slug");
  }
}
