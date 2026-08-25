package dev.dsbon.realworld.restclient.tests;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dsbon.realworld.restclient.ApiResponse;
import dev.dsbon.realworld.restclient.model.Dtos.ArticleResponse;
import dev.dsbon.realworld.restclient.model.Dtos.Comment;
import dev.dsbon.realworld.restclient.model.Dtos.CommentResponse;
import dev.dsbon.realworld.restclient.model.Dtos.CommentsResponse;
import dev.dsbon.realworld.restclient.model.Dtos.NewArticleRequest;
import dev.dsbon.realworld.restclient.model.Dtos.NewCommentRequest;
import dev.dsbon.realworld.restclient.model.Dtos.TagsResponse;
import dev.dsbon.realworld.restclient.support.BaseApiTest;
import dev.dsbon.realworld.restclient.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Comments and tags")
class CommentsAndTagsTest extends BaseApiTest {

  @Test
  @Tag("smoke")
  @DisplayName("a comment can be added, listed and deleted")
  void managesTheCommentLifecycle() {
    var user = registerFreshUser();
    String slug =
        user.session()
            .createArticle(
                NewArticleRequest.of(TestData.articleTitle(), "d", "b", TestData.tags()))
            .as(ArticleResponse.class)
            .article()
            .slug();
    String body = TestData.commentBody();

    ApiResponse created = user.session().addComment(slug, NewCommentRequest.of(body));
    assertThat(created.status()).as(created.describe()).isEqualTo(201);

    Comment comment = created.as(CommentResponse.class).comment();
    assertThat(comment.body()).isEqualTo(body);
    assertThat(comment.author().username()).isEqualTo(user.username());
    // Comment ids are JSON numbers here, not strings. Several RealWorld
    // implementations use strings, so the type is worth pinning.
    assertThat(comment.id()).isPositive();

    assertThat(user.session().comments(slug).as(CommentsResponse.class).comments())
        .extracting(Comment::body)
        .contains(body);

    assertThat(user.session().deleteComment(slug, comment.id()).status()).isEqualTo(204);

    // A delete that reports success but leaves the comment listed is exactly the
    // bug this second read exists to catch.
    assertThat(user.session().comments(slug).as(CommentsResponse.class).comments())
        .extracting(Comment::body)
        .doesNotContain(body);
  }

  @Test
  @Tag("contract")
  @DisplayName("commenting without a token returns 401")
  void rejectsAnonymousComment() {
    var user = registerFreshUser();
    String slug =
        user.session()
            .createArticle(
                NewArticleRequest.of(TestData.articleTitle(), "d", "b", TestData.tags()))
            .as(ArticleResponse.class)
            .article()
            .slug();

    assertThat(anonymous.addComment(slug, NewCommentRequest.of("nope")).status()).isEqualTo(401);
  }

  @Test
  @Tag("smoke")
  @DisplayName("GET /tags returns the tag catalogue without a token")
  void returnsTags() {
    ApiResponse response = anonymous.tags();

    assertThat(response.status()).as(response.describe()).isEqualTo(200);
    // One of the few genuinely public endpoints on this deployment, which is why
    // it doubles as the suite's connectivity check.
    assertThat(response.as(TagsResponse.class).tags())
        .isNotNull()
        .isNotEmpty()
        .allSatisfy(tag -> assertThat(tag).isNotBlank());
  }
}
