package dev.dsbon.realworld.api.tests;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dsbon.realworld.api.client.ApiResponse;
import dev.dsbon.realworld.api.model.Dtos.ArticleResponse;
import dev.dsbon.realworld.api.model.Dtos.CommentResponse;
import dev.dsbon.realworld.api.model.Dtos.CommentsResponse;
import dev.dsbon.realworld.api.model.Dtos.NewArticleRequest;
import dev.dsbon.realworld.api.model.Dtos.NewCommentRequest;
import dev.dsbon.realworld.api.model.Dtos.TagsResponse;
import dev.dsbon.realworld.api.support.BaseApiTest;
import dev.dsbon.realworld.api.support.TestData;
import org.testng.annotations.Test;

/** Comments on an article, plus the tag catalogue. */
public class CommentsAndTagsTest extends BaseApiTest {

  @Test(groups = "smoke", description = "A comment can be added, listed and deleted")
  public void managesTheCommentLifecycle() {
    var user = registerFreshUser();
    String slug =
        user.client()
            .createArticle(
                NewArticleRequest.of(TestData.articleTitle(), "d", "b", TestData.tags()))
            .as(ArticleResponse.class)
            .article()
            .slug();
    String body = TestData.commentBody();

    // --- create
    ApiResponse created = user.client().addComment(slug, NewCommentRequest.of(body));
    assertThat(created.status()).as(created.describe()).isEqualTo(201);

    var comment = created.as(CommentResponse.class).comment();
    assertThat(comment.body()).isEqualTo(body);
    assertThat(comment.author().username()).isEqualTo(user.username());
    // Comment ids are JSON numbers here. Several RealWorld implementations use
    // strings, so the type is worth pinning rather than assuming.
    assertThat(comment.id()).isPositive();

    // --- list
    ApiResponse listed = user.client().comments(slug);
    assertThat(listed.status()).as(listed.describe()).isEqualTo(200);
    assertThat(listed.as(CommentsResponse.class).comments())
        .extracting(c -> c.body())
        .contains(body);

    // --- delete
    assertThat(user.client().deleteComment(slug, comment.id()).status()).isEqualTo(204);

    // A delete that reports success but leaves the comment listed is exactly the
    // bug this second read exists to catch.
    assertThat(user.client().comments(slug).as(CommentsResponse.class).comments())
        .extracting(c -> c.body())
        .doesNotContain(body);
  }

  @Test(groups = "contract", description = "Commenting without a token returns 401")
  public void rejectsAnonymousComment() {
    var user = registerFreshUser();
    String slug =
        user.client()
            .createArticle(
                NewArticleRequest.of(TestData.articleTitle(), "d", "b", TestData.tags()))
            .as(ArticleResponse.class)
            .article()
            .slug();

    ApiResponse response = anonymous.addComment(slug, NewCommentRequest.of("should not be stored"));

    assertThat(response.status()).as(response.describe()).isEqualTo(401);
  }

  @Test(groups = "smoke", description = "GET /tags returns the tag catalogue without a token")
  public void returnsTags() {
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
