package dev.dsbon.realworld.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * The RealWorld contract as Java records.
 *
 * <p>Typing the payloads instead of poking at maps is the whole reason this
 * module carries Jackson. A typo in {@code favoritesCount} becomes a compile
 * error rather than a null at runtime, and the record definitions double as
 * executable documentation of the contract.
 *
 * <p>Every response record is annotated {@link JsonIgnoreProperties} with {@code
 * ignoreUnknown = true}. That is a deliberate contract-testing decision: a
 * server that ADDS a field must not break the suite, because adding a field is a
 * backwards-compatible change. Removing one still fails, because the assertions
 * read it.
 *
 * <p>Request records use {@code JsonInclude.NON_NULL} so that a partial update
 * sends only the fields it means to change, rather than nulling the rest.
 */
public final class Dtos {

  private Dtos() {}

  // ---------------------------------------------------------------- requests

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NewUserRequest(NewUser user) {
    public static NewUserRequest of(String username, String email, String password) {
      return new NewUserRequest(new NewUser(username, email, password));
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NewUser(String username, String email, String password) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record LoginRequest(LoginUser user) {
    public static LoginRequest of(String email, String password) {
      return new LoginRequest(new LoginUser(email, password));
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record LoginUser(String email, String password) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record UpdateUserRequest(UpdateUser user) {
    public static UpdateUserRequest bio(String bio) {
      return new UpdateUserRequest(new UpdateUser(null, null, null, bio, null));
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record UpdateUser(
      String username, String email, String password, String bio, String image) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NewArticleRequest(NewArticle article) {
    public static NewArticleRequest of(
        String title, String description, String body, List<String> tagList) {
      return new NewArticleRequest(new NewArticle(title, description, body, tagList));
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NewArticle(String title, String description, String body, List<String> tagList) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record UpdateArticleRequest(UpdateArticle article) {
    public static UpdateArticleRequest description(String description) {
      return new UpdateArticleRequest(new UpdateArticle(null, description, null));
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record UpdateArticle(String title, String description, String body) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NewCommentRequest(NewComment comment) {
    public static NewCommentRequest of(String body) {
      return new NewCommentRequest(new NewComment(body));
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NewComment(String body) {}

  // --------------------------------------------------------------- responses

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record UserResponse(User user) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record User(String email, String token, String username, String bio, String image) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ProfileResponse(Profile profile) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Profile(String username, String bio, String image, Boolean following) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ArticleResponse(Article article) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ArticlesResponse(List<Article> articles, Integer articlesCount) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Article(
      String slug,
      String title,
      String description,
      String body,
      List<String> tagList,
      String createdAt,
      String updatedAt,
      Boolean favorited,
      Integer favoritesCount,
      Profile author) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record CommentResponse(Comment comment) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record CommentsResponse(List<Comment> comments) {}

  /**
   * Comment ids are JSON <b>numbers</b> here, not strings — verified against the
   * live API. The original RealWorld specification is ambiguous about this and
   * several implementations use strings, so the type is pinned deliberately.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Comment(
      long id, String createdAt, String updatedAt, String body, Profile author) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record TagsResponse(List<String> tags) {}

  /**
   * Every error this API returns, in one shape:
   * {@code {"errors": {"field": ["message", ...]}}}
   *
   * <p>Consistent across 401, 404 and 422 — verified against the live service.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ErrorResponse(Map<String, List<String>> errors) {}
}
