package dev.dsbon.realworld.restassured.tests;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import dev.dsbon.realworld.restassured.support.BaseApiTest;
import dev.dsbon.realworld.restassured.support.TestData;
import io.restassured.specification.ResponseSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Comments and tags")
class CommentsAndTagsTest extends BaseApiTest {

  @Autowired ResponseSpecification okJson;
  @Autowired ResponseSpecification createdJson;

  @Test
  @Tag("smoke")
  @DisplayName("a comment can be added, listed and deleted")
  void managesTheCommentLifecycle() {
    var user = registerFreshUser();
    String slug =
        given()
            .spec(authenticated(user.token()))
            .body(TestData.newArticle(TestData.articleTitle()))
            .when()
            .post("/articles")
            .then()
            .statusCode(201)
            .extract()
            .path("article.slug");

    String body = TestData.commentBody();

    int commentId =
        given()
            .spec(authenticated(user.token()))
            .body(TestData.comment(body))
            .when()
            .post("/articles/{slug}/comments", slug)
            .then()
            .spec(createdJson)
            .body(matchesJsonSchemaInClasspath("schemas/comment-response.json"))
            .body("comment.body", equalTo(body))
            .body("comment.author.username", equalTo(user.username()))
            .extract()
            .path("comment.id");

    given()
        .spec(authenticated(user.token()))
        .when()
        .get("/articles/{slug}/comments", slug)
        .then()
        .spec(okJson)
        // GPath again: collect the `body` of every comment and assert membership,
        // without deserializing anything.
        .body("comments.body", hasItem(body));

    given()
        .spec(authenticated(user.token()))
        .when()
        .delete("/articles/{slug}/comments/{id}", slug, commentId)
        .then()
        .statusCode(204);

    // A delete that reports success but leaves the comment listed is exactly the
    // bug this second read exists to catch.
    given()
        .spec(authenticated(user.token()))
        .when()
        .get("/articles/{slug}/comments", slug)
        .then()
        .spec(okJson)
        .body("comments.body", not(hasItem(body)));
  }

  @Test
  @Tag("contract")
  @DisplayName("commenting without a token returns 401")
  void rejectsAnonymousComment() {
    var user = registerFreshUser();
    String slug =
        given()
            .spec(authenticated(user.token()))
            .body(TestData.newArticle(TestData.articleTitle()))
            .when()
            .post("/articles")
            .then()
            .statusCode(201)
            .extract()
            .path("article.slug");

    given()
        .spec(anonymousSpec)
        .body(TestData.comment("should not be stored"))
        .when()
        .post("/articles/{slug}/comments", slug)
        .then()
        .statusCode(401);
  }

  @Test
  @Tag("smoke")
  @DisplayName("GET /tags returns the tag catalogue without a token")
  void returnsTags() {
    given()
        .spec(anonymousSpec)
        .when()
        .get("/tags")
        .then()
        .spec(okJson)
        // One of the few genuinely public endpoints on this deployment, which is
        // why it doubles as the suite's connectivity check.
        .body("tags.size()", greaterThan(0));
  }
}
