package dev.dsbon.realworld.api.client;

import dev.dsbon.realworld.api.model.Dtos.LoginRequest;
import dev.dsbon.realworld.api.model.Dtos.NewArticleRequest;
import dev.dsbon.realworld.api.model.Dtos.NewCommentRequest;
import dev.dsbon.realworld.api.model.Dtos.NewUserRequest;
import dev.dsbon.realworld.api.model.Dtos.UpdateArticleRequest;
import dev.dsbon.realworld.api.model.Dtos.UpdateUserRequest;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A typed client for the RealWorld API, built on {@link java.net.http.HttpClient}.
 *
 * <p>This is the "no framework" baseline of the repository: the JDK's own HTTP
 * client, Jackson for JSON, and about a hundred lines of glue. Everything REST
 * Assured or Spring's {@code RestClient} would do for you is visible here, which
 * is exactly the point of the comparison.
 *
 * <p>Three decisions worth calling out:
 *
 * <ul>
 *   <li><b>Never throws on a non-2xx.</b> In an API suite the status code is
 *       usually the assertion, so it is data, not an error.
 *   <li><b>The token lives on the client, not in every call.</b> {@link
 *       #withToken(String)} returns a new instance — the client is immutable, so
 *       an authenticated and an anonymous client can coexist without either
 *       leaking into the other.
 *   <li><b>{@code HttpClient} is shared and static.</b> It owns a connection
 *       pool and a thread; creating one per request would be slower and would
 *       leak threads across a long run.
 * </ul>
 */
public final class RealWorldClient {

  private static final HttpClient HTTP =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          // The API answers 3xx on some malformed paths; following them would
          // silently turn a routing bug into a passing test.
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  private final String baseUrl;
  private final String token;

  private RealWorldClient(String baseUrl, String token) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.token = token;
  }

  /** An anonymous client pointed at the configured base URL. */
  public static RealWorldClient anonymous(String baseUrl) {
    return new RealWorldClient(baseUrl, null);
  }

  /** A copy of this client that authenticates every request. */
  public RealWorldClient withToken(String token) {
    return new RealWorldClient(baseUrl, token);
  }

  public boolean isAuthenticated() {
    return token != null;
  }

  // ------------------------------------------------------------------- users

  public ApiResponse register(NewUserRequest request) {
    return post("/users", request);
  }

  public ApiResponse login(LoginRequest request) {
    return post("/users/login", request);
  }

  public ApiResponse currentUser() {
    return get("/user");
  }

  public ApiResponse updateUser(UpdateUserRequest request) {
    return put("/user", request);
  }

  // ---------------------------------------------------------------- profiles

  public ApiResponse profile(String username) {
    return get("/profiles/" + segment(username));
  }

  public ApiResponse follow(String username) {
    return post("/profiles/" + segment(username) + "/follow", null);
  }

  public ApiResponse unfollow(String username) {
    return delete("/profiles/" + segment(username) + "/follow");
  }

  // ---------------------------------------------------------------- articles

  public ApiResponse createArticle(NewArticleRequest request) {
    return post("/articles", request);
  }

  public ApiResponse article(String slug) {
    return get("/articles/" + segment(slug));
  }

  public ApiResponse updateArticle(String slug, UpdateArticleRequest request) {
    return put("/articles/" + segment(slug), request);
  }

  public ApiResponse deleteArticle(String slug) {
    return delete("/articles/" + segment(slug));
  }

  /** Query parameters are optional; pass an empty map for an unfiltered list. */
  public ApiResponse listArticles(Map<String, String> query) {
    return get("/articles" + queryString(query));
  }

  public ApiResponse feed(Map<String, String> query) {
    return get("/articles/feed" + queryString(query));
  }

  public ApiResponse favorite(String slug) {
    return post("/articles/" + segment(slug) + "/favorite", null);
  }

  public ApiResponse unfavorite(String slug) {
    return delete("/articles/" + segment(slug) + "/favorite");
  }

  // ---------------------------------------------------------------- comments

  public ApiResponse addComment(String slug, NewCommentRequest request) {
    return post("/articles/" + segment(slug) + "/comments", request);
  }

  public ApiResponse comments(String slug) {
    return get("/articles/" + segment(slug) + "/comments");
  }

  public ApiResponse deleteComment(String slug, long commentId) {
    return delete("/articles/" + segment(slug) + "/comments/" + commentId);
  }

  // -------------------------------------------------------------------- tags

  public ApiResponse tags() {
    return get("/tags");
  }

  // ----------------------------------------------------------------- plumbing

  private ApiResponse get(String path) {
    return send(builder(path).GET());
  }

  private ApiResponse post(String path, Object body) {
    return send(builder(path).POST(bodyOf(body)));
  }

  private ApiResponse put(String path, Object body) {
    return send(builder(path).PUT(bodyOf(body)));
  }

  private ApiResponse delete(String path) {
    return send(builder(path).DELETE());
  }

  private HttpRequest.Builder builder(String path) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json");
    if (token != null) {
      // NOT "Bearer". RealWorld's scheme is literally `Token <value>`, and the
      // value this deployment issues is not a JWT despite what its own OpenAPI
      // description claims — see docs/contract-findings.md.
      builder.header("Authorization", "Token " + token);
    }
    return builder;
  }

  private static HttpRequest.BodyPublisher bodyOf(Object body) {
    return body == null
        ? HttpRequest.BodyPublishers.noBody()
        : HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8);
  }

  private ApiResponse send(HttpRequest.Builder builder) {
    HttpRequest request = builder.header("Content-Type", "application/json").build();
    try {
      HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
      return new ApiResponse(response.statusCode(), response.body(), response.headers().map());
    } catch (IOException e) {
      throw new IllegalStateException(
          "%s %s failed at the transport layer".formatted(request.method(), request.uri()), e);
    } catch (InterruptedException e) {
      // Restore the flag rather than swallowing it: a test runner that is
      // shutting down should not be blocked by a swallowed interrupt.
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while calling " + request.uri(), e);
    }
  }

  private static String queryString(Map<String, String> query) {
    if (query == null || query.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder("?");
    Map<String, String> ordered = new LinkedHashMap<>(query);
    ordered.forEach(
        (k, v) -> {
          if (sb.length() > 1) {
            sb.append('&');
          }
          sb.append(encode(k)).append('=').append(encode(v));
        });
    return sb.toString();
  }

  /** Query-string encoding: {@code application/x-www-form-urlencoded}, where {@code +} means space. */
  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  /**
   * Path-segment encoding.
   *
   * <p>{@link URLEncoder} is a <i>form</i> encoder: it turns a space into {@code
   * +}, which inside a path means a literal plus sign, not a space. Slugs and
   * usernames in this API never contain one, but relying on that is how a suite
   * acquires a bug that only appears the day someone adds a test with an unusual
   * title.
   */
  private static String segment(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
