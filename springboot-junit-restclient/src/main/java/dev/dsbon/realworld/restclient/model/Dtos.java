package dev.dsbon.realworld.restclient.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * The RealWorld contract as Java records.
 *
 * <p>Deliberately a copy of the equivalent file in the other two modules rather
 * than a shared dependency. Extracting it would couple three implementations
 * that exist precisely to be compared — and the first time one framework needed
 * a Jackson annotation the other two did not, the shared module would sprout a
 * conditional and stop being shared in any useful sense.
 *
 * <p>{@code ignoreUnknown = true} on responses is a contract-testing decision: a
 * server ADDING a field is backwards compatible and must not break the suite.
 * Removing one still fails, because the assertions read it.
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

  /** Comment ids are JSON numbers here, not strings — verified against the live API. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Comment(long id, String createdAt, String updatedAt, String body, Profile author) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record TagsResponse(List<String> tags) {}

  /** Every error, at every status: {@code {"errors": {"field": ["message"]}}}. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ErrorResponse(Map<String, List<String>> errors) {}
}
