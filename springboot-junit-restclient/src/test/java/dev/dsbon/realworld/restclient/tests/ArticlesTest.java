package dev.dsbon.realworld.restclient.tests;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dsbon.realworld.restclient.ApiResponse;
import dev.dsbon.realworld.restclient.RealWorldApi;
import dev.dsbon.realworld.restclient.model.Dtos.ArticleResponse;
import dev.dsbon.realworld.restclient.model.Dtos.ArticlesResponse;
import dev.dsbon.realworld.restclient.model.Dtos.NewArticleRequest;
import dev.dsbon.realworld.restclient.model.Dtos.UpdateArticleRequest;
import dev.dsbon.realworld.restclient.support.BaseApiTest;
import dev.dsbon.realworld.restclient.support.TestData;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Articles")
class ArticlesTest extends BaseApiTest {

  @Test
  @Tag("smoke")
  @DisplayName("creating an article returns 201 and the derived slug")
  void createsAnArticle() {
    var user = registerFreshUser();
    String title = TestData.articleTitle();

    ApiResponse response =
        user.session()
            .createArticle(NewArticleRequest.of(title, "A description", "A body", TestData.tags()));

    assertThat(response.status()).as(response.describe()).isEqualTo(201);

    var article = response.as(ArticleResponse.class).article();
    assertThat(article.title()).isEqualTo(title);
    // The slug is derived, not echoed — see TestData.slugFor for the exact rule.
    assertThat(article.slug()).isEqualTo(TestData.slugFor(title));
    assertThat(article.tagList()).containsExactlyInAnyOrderElementsOf(TestData.tags());
    assertThat(article.favorited()).isFalse();
    assertThat(article.favoritesCount()).isZero();
    assertThat(article.author().username()).isEqualTo(user.username());
  }

  @Test
  @Tag("smoke")
  @DisplayName("creating an article without a token returns 401")
  void rejectsAnonymousCreation() {
    ApiResponse response =
        anonymous.createArticle(
            NewArticleRequest.of(TestData.articleTitle(), "d", "b", TestData.tags()));

    assertThat(response.status()).as(response.describe()).isEqualTo(401);
    assertThat(response.asError().errors()).containsKey("token");
  }

  @Test
  @Tag("contract")
  @DisplayName("an article can be read back by its slug")
  void readsAnArticleBySlug() {
    var user = registerFreshUser();
    String title = TestData.articleTitle();
    String slug = createArticle(user.session(), title);

    // Authenticated on purpose: this deployment resolves reads against the
    // current session and 404s an anonymous GET of a real article. The anonymous
    // case is asserted separately in ContractGapsTest.
    ApiResponse response = user.session().article(slug);

    assertThat(response.status()).as(response.describe()).isEqualTo(200);
    assertThat(response.as(ArticleResponse.class).article().title()).isEqualTo(title);
  }

  @Test
  @Tag("contract")
  @DisplayName("an unknown slug returns 404 with an errors body")
  void returns404ForUnknownSlug() {
    var user = registerFreshUser();

    ApiResponse response = user.session().article("no-such-slug-" + System.nanoTime());

    assertThat(response.status()).as(response.describe()).isEqualTo(404);
    assertThat(response.asError().errors()).containsKey("article");
  }

  @Test
  @Tag("contract")
  @DisplayName("updating an article changes only the fields sent")
  void updatesAnArticle() {
    var user = registerFreshUser();
    String title = TestData.articleTitle();
    String slug = createArticle(user.session(), title);

    ApiResponse response =
        user.session().updateArticle(slug, UpdateArticleRequest.description("updated description"));

    assertThat(response.status()).as(response.describe()).isEqualTo(200);

    var updated = response.as(ArticleResponse.class).article();
    assertThat(updated.description()).isEqualTo("updated description");
    assertThat(updated.title()).isEqualTo(title);
  }

  @Test
  @Tag("smoke")
  @DisplayName("deleting an article returns 204 and the article is gone")
  void deletesAnArticle() {
    var user = registerFreshUser();
    String slug = createArticle(user.session(), TestData.articleTitle());

    assertThat(user.session().deleteArticle(slug).status()).isEqualTo(204);

    // A delete that returns 204 but leaves the resource readable is a bug that
    // only a follow-up read catches.
    assertThat(user.session().article(slug).status()).isEqualTo(404);
  }

  @Test
  @Tag("contract")
  @DisplayName("favouriting toggles the flag and the counter")
  void favouritesAndUnfavourites() {
    var user = registerFreshUser();
    String slug = createArticle(user.session(), TestData.articleTitle());

    var favorited = user.session().favorite(slug).as(ArticleResponse.class).article();
    assertThat(favorited.favorited()).isTrue();
    assertThat(favorited.favoritesCount()).isEqualTo(1);

    var unfavorited = user.session().unfavorite(slug).as(ArticleResponse.class).article();
    assertThat(unfavorited.favorited()).isFalse();
    assertThat(unfavorited.favoritesCount()).isZero();
  }

  @Test
  @Tag("smoke")
  @DisplayName("listing articles returns a page and a total count")
  void listsArticles() {
    ApiResponse response = anonymous.listArticles(Map.of("limit", "3"));

    assertThat(response.status()).as(response.describe()).isEqualTo(200);

    ArticlesResponse body = response.as(ArticlesResponse.class);
    assertThat(body.articles()).isNotNull().hasSizeLessThanOrEqualTo(3);
    assertThat(body.articlesCount()).isNotNull().isGreaterThanOrEqualTo(body.articles().size());
  }

  @Test
  @Tag("smoke")
  @DisplayName("the personal feed requires authentication")
  void feedRequiresAuthentication() {
    assertThat(anonymous.feed(Map.of()).status()).isEqualTo(401);

    var user = registerFreshUser();
    ApiResponse response = user.session().feed(Map.of("limit", "5"));

    assertThat(response.status()).as(response.describe()).isEqualTo(200);
    // A brand-new account follows nobody, so an empty feed is correct. The
    // assertion is on the shape, not the content.
    assertThat(response.as(ArticlesResponse.class).articles()).isNotNull();
  }

  private String createArticle(RealWorldApi.Session session, String title) {
    return session
        .createArticle(NewArticleRequest.of(title, "d", "b", TestData.tags()))
        .as(ArticleResponse.class)
        .article()
        .slug();
  }
}
