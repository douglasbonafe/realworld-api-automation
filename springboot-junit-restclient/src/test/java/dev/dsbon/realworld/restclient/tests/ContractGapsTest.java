package dev.dsbon.realworld.restclient.tests;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dsbon.realworld.restclient.ApiResponse;
import dev.dsbon.realworld.restclient.model.Dtos.ArticleResponse;
import dev.dsbon.realworld.restclient.model.Dtos.ArticlesResponse;
import dev.dsbon.realworld.restclient.model.Dtos.NewArticleRequest;
import dev.dsbon.realworld.restclient.model.Dtos.NewUserRequest;
import dev.dsbon.realworld.restclient.support.BaseApiTest;
import dev.dsbon.realworld.restclient.support.TestData;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Where api.realworld.show disagrees with its own published contract.
 *
 * <p>Excluded from the default run via {@code <excludedGroups>} in the module
 * pom. They are not flaky — they are reliable, and they fail. Excluding them
 * keeps a red build meaningful instead of turning it into a permanent block of
 * known defects.
 *
 * <p>Run them deliberately to check whether a gap has closed:
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
    String slug =
        user.session()
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

  @Test
  @Tag("contract-gaps")
  @DisplayName("GET /articles/{slug}/comments should be readable without a token")
  void commentsShouldBeReadableAnonymously() {
    var user = registerFreshUser();
    String slug =
        user.session()
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

  @Test
  @Tag("contract-gaps")
  @DisplayName("registering a duplicate username should return 409")
  void duplicateUsernameShouldConflict() {
    var user = registerFreshUser();

    ApiResponse response =
        anonymous.register(
            NewUserRequest.of(user.username(), "other-" + user.email(), TestData.password()));

    assertThat(response.status())
        .as(
            "The OpenAPI document lists 409 for POST /users. This deployment accepts the "
                + "duplicate and returns 201, so usernames are not unique. Observed: %s",
            response.describe())
        .isEqualTo(409);
  }

  @Test
  @Tag("contract-gaps")
  @DisplayName("the ?author= filter should return that author's articles")
  void authorFilterShouldReturnResults() {
    var user = registerFreshUser();
    user.session()
        .createArticle(NewArticleRequest.of(TestData.articleTitle(), "d", "b", TestData.tags()));

    ApiResponse response = user.session().listArticles(Map.of("author", user.username()));

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.as(ArticlesResponse.class).articles())
        .as("?author= returns an empty page even though the author has articles. "
            + "?tag= and ?favorited= behave the same way.")
        .isNotEmpty();
  }

  @Test
  @Tag("contract-gaps")
  @Tag("multi-user")
  @DisplayName("a second user should be able to follow the first")
  void followingShouldWork() {
    var author = registerFreshUser();
    var follower = registerFreshUser();

    ApiResponse response = follower.session().follow(author.username());

    assertThat(response.status())
        .as(
            "Following needs two identities alive at once. This sandbox keeps ONE global "
                + "session, so by the time the follower exists the author's profile is no "
                + "longer resolvable and the call 404s. Observed: %s",
            response.describe())
        .isEqualTo(200);
  }
}
