package dev.dsbon.realworld.api.tests;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dsbon.realworld.api.client.ApiResponse;
import dev.dsbon.realworld.api.model.Dtos.ArticleResponse;
import dev.dsbon.realworld.api.model.Dtos.ArticlesResponse;
import dev.dsbon.realworld.api.model.Dtos.NewArticleRequest;
import dev.dsbon.realworld.api.model.Dtos.UpdateArticleRequest;
import dev.dsbon.realworld.api.support.BaseApiTest;
import dev.dsbon.realworld.api.support.TestData;
import java.util.Map;
import org.testng.annotations.Test;

/** Article creation, retrieval, update, deletion and favouriting. */
public class ArticlesTest extends BaseApiTest {

  @Test(groups = "smoke", description = "Creating an article returns 201 and the derived slug")
  public void createsAnArticle() {
    var user = registerFreshUser();
    String title = TestData.articleTitle();

    ApiResponse response =
        user.client()
            .createArticle(NewArticleRequest.of(title, "A description", "A body", TestData.tags()));

    assertThat(response.status()).as(response.describe()).isEqualTo(201);

    var article = response.as(ArticleResponse.class).article();
    assertThat(article.title()).isEqualTo(title);
    // The slug is derived, not echoed — see TestData.slugFor for the exact rule,
    // which was reverse-engineered from the live service.
    assertThat(article.slug()).isEqualTo(TestData.slugFor(title));
    assertThat(article.tagList()).containsExactlyInAnyOrderElementsOf(TestData.tags());
    assertThat(article.favorited()).isFalse();
    assertThat(article.favoritesCount()).isZero();
    assertThat(article.author().username()).isEqualTo(user.username());
    assertThat(article.createdAt()).isNotBlank();
  }

  @Test(groups = "smoke", description = "Creating an article without a token returns 401")
  public void rejectsAnonymousCreation() {
    ApiResponse response =
        anonymous.createArticle(
            NewArticleRequest.of(TestData.articleTitle(), "d", "b", TestData.tags()));

    assertThat(response.status()).as(response.describe()).isEqualTo(401);
    assertThat(response.asError().errors()).containsKey("token");
  }

  @Test(groups = "contract", description = "An article can be read back by its slug")
  public void readsAnArticleBySlug() {
    var user = registerFreshUser();
    String title = TestData.articleTitle();
    String slug =
        user.client()
            .createArticle(NewArticleRequest.of(title, "d", "b", TestData.tags()))
            .as(ArticleResponse.class)
            .article()
            .slug();

    // Authenticated on purpose. This deployment resolves reads against the
    // current session and answers 404 to an anonymous GET of a real article,
    // which contradicts both the RealWorld spec and its own OpenAPI document.
    // The anonymous case is asserted separately in ContractGapsTest.
    ApiResponse response = user.client().article(slug);

    assertThat(response.status()).as(response.describe()).isEqualTo(200);
    assertThat(response.as(ArticleResponse.class).article().title()).isEqualTo(title);
  }

  @Test(groups = "contract", description = "An unknown slug returns 404 with an errors body")
  public void returns404ForUnknownSlug() {
    var user = registerFreshUser();

    ApiResponse response = user.client().article("definitely-not-a-real-slug-" + System.nanoTime());

    assertThat(response.status()).as(response.describe()).isEqualTo(404);
    assertThat(response.asError().errors()).containsKey("article");
  }

  @Test(groups = "contract", description = "Updating an article changes only the fields sent")
  public void updatesAnArticle() {
    var user = registerFreshUser();
    String title = TestData.articleTitle();
    String slug =
        user.client()
            .createArticle(NewArticleRequest.of(title, "original", "b", TestData.tags()))
            .as(ArticleResponse.class)
            .article()
            .slug();

    ApiResponse response =
        user.client().updateArticle(slug, UpdateArticleRequest.description("updated description"));

    assertThat(response.status()).as(response.describe()).isEqualTo(200);

    var updated = response.as(ArticleResponse.class).article();
    assertThat(updated.description()).isEqualTo("updated description");
    // The untouched fields must survive the partial update.
    assertThat(updated.title()).isEqualTo(title);
    assertThat(updated.body()).isEqualTo("b");
  }

  @Test(groups = "smoke", description = "Deleting an article returns 204 and the article is gone")
  public void deletesAnArticle() {
    var user = registerFreshUser();
    String slug =
        user.client()
            .createArticle(
                NewArticleRequest.of(TestData.articleTitle(), "d", "b", TestData.tags()))
            .as(ArticleResponse.class)
            .article()
            .slug();

    assertThat(user.client().deleteArticle(slug).status()).isEqualTo(204);

    // A delete that returns 204 but leaves the resource readable is a bug that
    // only a follow-up read catches.
    assertThat(user.client().article(slug).status()).isEqualTo(404);
  }

  @Test(groups = "contract", description = "Favouriting toggles the flag and the counter")
  public void favouritesAndUnfavouritesAnArticle() {
    var user = registerFreshUser();
    String slug =
        user.client()
            .createArticle(
                NewArticleRequest.of(TestData.articleTitle(), "d", "b", TestData.tags()))
            .as(ArticleResponse.class)
            .article()
            .slug();

    ApiResponse favorited = user.client().favorite(slug);
    assertThat(favorited.status()).as(favorited.describe()).isEqualTo(200);
    var afterFavorite = favorited.as(ArticleResponse.class).article();
    assertThat(afterFavorite.favorited()).isTrue();
    assertThat(afterFavorite.favoritesCount()).isEqualTo(1);

    ApiResponse unfavorited = user.client().unfavorite(slug);
    assertThat(unfavorited.status()).as(unfavorited.describe()).isEqualTo(200);
    var afterUnfavorite = unfavorited.as(ArticleResponse.class).article();
    assertThat(afterUnfavorite.favorited()).isFalse();
    assertThat(afterUnfavorite.favoritesCount()).isZero();
  }

  @Test(groups = "smoke", description = "Listing articles returns a page and a total count")
  public void listsArticles() {
    ApiResponse response = anonymous.listArticles(Map.of("limit", "3"));

    assertThat(response.status()).as(response.describe()).isEqualTo(200);

    ArticlesResponse body = response.as(ArticlesResponse.class);
    assertThat(body.articles()).isNotNull().hasSizeLessThanOrEqualTo(3);
    assertThat(body.articlesCount()).isNotNull().isGreaterThanOrEqualTo(body.articles().size());
  }

  @Test(groups = "contract", description = "limit and offset produce disjoint pages")
  public void paginatesArticles() {
    var first = anonymous.listArticles(Map.of("limit", "1", "offset", "0")).as(ArticlesResponse.class);
    var second = anonymous.listArticles(Map.of("limit", "1", "offset", "1")).as(ArticlesResponse.class);

    // Only meaningful once the corpus holds at least two articles. Skipping the
    // assertion rather than failing keeps the test honest against a freshly
    // reset backend instead of encoding an assumption about seed data.
    if (first.articlesCount() != null && first.articlesCount() >= 2) {
      assertThat(first.articles()).hasSize(1);
      assertThat(second.articles()).hasSize(1);
      assertThat(first.articles().getFirst().slug())
          .as("offset=0 and offset=1 must not return the same article")
          .isNotEqualTo(second.articles().getFirst().slug());
    }
  }

  @Test(groups = "smoke", description = "The personal feed requires authentication")
  public void feedRequiresAuthentication() {
    assertThat(anonymous.feed(Map.of()).status()).isEqualTo(401);

    var user = registerFreshUser();
    ApiResponse response = user.client().feed(Map.of("limit", "5"));

    assertThat(response.status()).as(response.describe()).isEqualTo(200);
    // A brand-new account follows nobody, so its feed is legitimately empty —
    // the assertion is on the shape, not on content.
    assertThat(response.as(ArticlesResponse.class).articles()).isNotNull();
  }
}
