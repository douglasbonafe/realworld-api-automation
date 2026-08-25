package dev.dsbon.realworld.api.tests;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dsbon.realworld.api.client.ApiResponse;
import dev.dsbon.realworld.api.model.Dtos.ArticleResponse;
import dev.dsbon.realworld.api.model.Dtos.ArticlesResponse;
import dev.dsbon.realworld.api.model.Dtos.NewArticleRequest;
import dev.dsbon.realworld.api.model.Dtos.NewUserRequest;
import dev.dsbon.realworld.api.support.BaseApiTest;
import dev.dsbon.realworld.api.support.TestData;
import java.util.Map;
import org.testng.annotations.Test;

/**
 * The places where api.realworld.show disagrees with its own published contract.
 *
 * <p>These tests are in the {@code contract-gaps} group and are <b>excluded from
 * the default run</b>. They are not skipped because they are unreliable — they
 * are reliable, and they fail. They are excluded so the everyday suite reports
 * genuine regressions rather than a permanent block of known defects.
 *
 * <p>Each test states what the contract promises, asserts it, and carries the
 * observed behaviour in its message. Run them deliberately to check whether a
 * gap has been closed:
 *
 * <pre>
 *   mvn test -Dgroups=contract-gaps
 * </pre>
 *
 * <p>Against a spec-compliant self-hosted RealWorld backend these should pass.
 * Full write-up in docs/contract-findings.md.
 */
public class ContractGapsTest extends BaseApiTest {

  @Test(
      groups = "contract-gaps",
      description = "GET /articles/{slug} is documented as public but 404s without a token")
  public void articleShouldBeReadableAnonymously() {
    var user = registerFreshUser();
    String slug =
        user.client()
            .createArticle(
                NewArticleRequest.of(TestData.articleTitle(), "d", "b", TestData.tags()))
            .as(ArticleResponse.class)
            .article()
            .slug();

    ApiResponse response = anonymous.article(slug);

    assertThat(response.status())
        .as(
            "The RealWorld spec and this service's own OpenAPI document both list "
                + "GET /articles/{slug} as public (200/422, no 401 or 404). Observed: %s",
            response.describe())
        .isEqualTo(200);
  }

  @Test(
      groups = "contract-gaps",
      description = "GET /articles/{slug}/comments is documented as public but 404s without a token")
  public void commentsShouldBeReadableAnonymously() {
    var user = registerFreshUser();
    String slug =
        user.client()
            .createArticle(
                NewArticleRequest.of(TestData.articleTitle(), "d", "b", TestData.tags()))
            .as(ArticleResponse.class)
            .article()
            .slug();

    ApiResponse response = anonymous.comments(slug);

    assertThat(response.status())
        .as("Comments are a public read in the RealWorld spec. Observed: %s", response.describe())
        .isEqualTo(200);
  }

  @Test(
      groups = "contract-gaps",
      description = "Registering a duplicate username should return 409, not 201")
  public void duplicateUsernameShouldConflict() {
    var user = registerFreshUser();

    ApiResponse response =
        anonymous.register(
            NewUserRequest.of(
                user.username(), "other-" + user.email(), TestData.password()));

    assertThat(response.status())
        .as(
            "The OpenAPI document lists 409 for POST /users. This deployment accepts the "
                + "duplicate and returns 201, so usernames are not unique. Observed: %s",
            response.describe())
        .isEqualTo(409);
  }

  @Test(
      groups = "contract-gaps",
      description = "The ?author= filter should return that author's articles")
  public void authorFilterShouldReturnResults() {
    var user = registerFreshUser();
    user.client()
        .createArticle(NewArticleRequest.of(TestData.articleTitle(), "d", "b", TestData.tags()));

    ApiResponse response = user.client().listArticles(Map.of("author", user.username()));

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.as(ArticlesResponse.class).articles())
        .as(
            "The author has at least one article, but ?author= returns an empty page. "
                + "?tag= and ?favorited= behave the same way.")
        .isNotEmpty();
  }

  @Test(
      groups = {"contract-gaps", "multi-user"},
      description = "A second user should be able to follow the first")
  public void followingShouldWork() {
    var author = registerFreshUser();
    var follower = registerFreshUser();

    ApiResponse response = follower.client().follow(author.username());

    assertThat(response.status())
        .as(
            "Following needs two identities to exist at once. This sandbox keeps ONE "
                + "global session, so by the time the follower is registered the author's "
                + "profile is no longer resolvable and the call 404s. Observed: %s",
            response.describe())
        .isEqualTo(200);
  }

  @Test(
      groups = {"contract-gaps", "multi-user"},
      description = "A user should not be able to modify another user's article")
  public void ownershipShouldBeEnforced() {
    var author = registerFreshUser();
    String slug =
        author
            .client()
            .createArticle(
                NewArticleRequest.of(TestData.articleTitle(), "d", "b", TestData.tags()))
            .as(ArticleResponse.class)
            .article()
            .slug();

    var attacker = registerFreshUser();
    ApiResponse response =
        attacker
            .client()
            .updateArticle(slug, dev.dsbon.realworld.api.model.Dtos.UpdateArticleRequest.description("hijacked"));

    assertThat(response.status())
        .as(
            "A non-author editing an article should be rejected with 403. Note this cannot "
                + "be proven on the shared sandbox — the single global session means the "
                + "'attacker' IS the current user. Meaningful only against a self-hosted "
                + "backend. Observed: %s",
            response.describe())
        .isEqualTo(403);
  }
}
