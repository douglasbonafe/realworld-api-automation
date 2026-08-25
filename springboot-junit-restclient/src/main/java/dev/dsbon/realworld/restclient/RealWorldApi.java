package dev.dsbon.realworld.restclient;

import dev.dsbon.realworld.restclient.model.Dtos.LoginRequest;
import dev.dsbon.realworld.restclient.model.Dtos.NewArticleRequest;
import dev.dsbon.realworld.restclient.model.Dtos.NewCommentRequest;
import dev.dsbon.realworld.restclient.model.Dtos.NewUserRequest;
import dev.dsbon.realworld.restclient.model.Dtos.UpdateArticleRequest;
import dev.dsbon.realworld.restclient.model.Dtos.UpdateUserRequest;
import java.util.Map;
import java.util.function.Function;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;
// tools.jackson, not com.fasterxml.jackson: Spring Boot 4 ships Jackson 3, which
// relocated core and databind. Annotations kept their old package — see ApiResponse.
import tools.jackson.databind.ObjectMapper;

/**
 * A typed facade over the RealWorld API, built on Spring's {@link RestClient}.
 *
 * <p>Requests are issued through {@code .retrieve().toEntity(String.class)} —
 * the body is deserialized later, by {@link ApiResponse}, rather than here. That
 * is intentional: a failing request has a body that will not parse into the
 * success type, and eager deserialization would replace a clear "expected 200,
 * got 422" with an opaque Jackson stack trace.
 *
 * <p>Authentication is expressed as {@link #as(String)}, which returns a {@link
 * Session} bound to one token. A test can hold an authenticated and an anonymous
 * view of the API at once without either leaking into the other.
 */
@Component
public class RealWorldApi {

  private final RestClient client;
  private final ObjectMapper mapper;

  public RealWorldApi(RestClient realWorldRestClient, ObjectMapper mapper) {
    this.client = realWorldRestClient;
    this.mapper = mapper;
  }

  /** An anonymous view — no {@code Authorization} header is sent. */
  public Session anonymous() {
    return new Session(null);
  }

  /** A view that authenticates every request with the given token. */
  public Session as(String token) {
    return new Session(token);
  }

  /** One caller's view of the API. Immutable; created through {@link #as(String)}. */
  public final class Session {

    private final String token;

    private Session(String token) {
      this.token = token;
    }

    public boolean isAuthenticated() {
      return token != null;
    }

    // ----------------------------------------------------------------- users

    public ApiResponse register(NewUserRequest request) {
      return exchange(HttpMethod.POST, uri("/users"), request);
    }

    public ApiResponse login(LoginRequest request) {
      return exchange(HttpMethod.POST, uri("/users/login"), request);
    }

    public ApiResponse currentUser() {
      return exchange(HttpMethod.GET, uri("/user"), null);
    }

    public ApiResponse updateUser(UpdateUserRequest request) {
      return exchange(HttpMethod.PUT, uri("/user"), request);
    }

    // -------------------------------------------------------------- profiles

    public ApiResponse profile(String username) {
      return exchange(HttpMethod.GET, path("/profiles/{username}", username), null);
    }

    public ApiResponse follow(String username) {
      return exchange(HttpMethod.POST, path("/profiles/{username}/follow", username), null);
    }

    public ApiResponse unfollow(String username) {
      return exchange(HttpMethod.DELETE, path("/profiles/{username}/follow", username), null);
    }

    // -------------------------------------------------------------- articles

    public ApiResponse createArticle(NewArticleRequest request) {
      return exchange(HttpMethod.POST, uri("/articles"), request);
    }

    public ApiResponse article(String slug) {
      return exchange(HttpMethod.GET, path("/articles/{slug}", slug), null);
    }

    public ApiResponse updateArticle(String slug, UpdateArticleRequest request) {
      return exchange(HttpMethod.PUT, path("/articles/{slug}", slug), request);
    }

    public ApiResponse deleteArticle(String slug) {
      return exchange(HttpMethod.DELETE, path("/articles/{slug}", slug), null);
    }

    public ApiResponse listArticles(Map<String, String> query) {
      return exchange(HttpMethod.GET, query("/articles", query), null);
    }

    public ApiResponse feed(Map<String, String> query) {
      return exchange(HttpMethod.GET, query("/articles/feed", query), null);
    }

    public ApiResponse favorite(String slug) {
      return exchange(HttpMethod.POST, path("/articles/{slug}/favorite", slug), null);
    }

    public ApiResponse unfavorite(String slug) {
      return exchange(HttpMethod.DELETE, path("/articles/{slug}/favorite", slug), null);
    }

    // -------------------------------------------------------------- comments

    public ApiResponse addComment(String slug, NewCommentRequest request) {
      return exchange(HttpMethod.POST, path("/articles/{slug}/comments", slug), request);
    }

    public ApiResponse comments(String slug) {
      return exchange(HttpMethod.GET, path("/articles/{slug}/comments", slug), null);
    }

    public ApiResponse deleteComment(String slug, long commentId) {
      return exchange(
          HttpMethod.DELETE,
          b -> b.path("/articles/{slug}/comments/{id}").build(slug, commentId),
          null);
    }

    // ------------------------------------------------------------------ tags

    public ApiResponse tags() {
      return exchange(HttpMethod.GET, uri("/tags"), null);
    }

    // -------------------------------------------------------------- plumbing

    private ApiResponse exchange(
        HttpMethod method, Function<UriBuilder, java.net.URI> uri, Object body) {

      RestClient.RequestBodySpec spec = client.method(method).uri(uri);

      if (token != null) {
        // NOT "Bearer". RealWorld's scheme is literally `Token <value>`, and the
        // value this deployment issues is not a JWT despite its own OpenAPI
        // description saying so. See docs/contract-findings.md.
        spec = spec.header(HttpHeaders.AUTHORIZATION, "Token " + token);
      }
      if (body != null) {
        spec = spec.contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(body);
      }

      // No status handler here: the bean is configured with a catch-all no-op
      // handler, so 4xx and 5xx come back as data instead of exceptions.
      ResponseEntity<String> response = spec.retrieve().toEntity(String.class);

      return new ApiResponse(
          response.getStatusCode().value(), response.getBody(), response.getHeaders(), mapper);
    }

    private Function<UriBuilder, java.net.URI> uri(String path) {
      return b -> b.path(path).build();
    }

    /**
     * A path with URI template variables.
     *
     * <p>Passing the value as a variable rather than concatenating it means Spring
     * encodes the segment correctly. Concatenation is how a slug containing a
     * space or a slash quietly becomes a request to a different resource.
     */
    private Function<UriBuilder, java.net.URI> path(String template, Object... values) {
      return b -> b.path(template).build(values);
    }

    private Function<UriBuilder, java.net.URI> query(String path, Map<String, String> query) {
      return b -> {
        UriBuilder builder = b.path(path);
        if (query != null) {
          query.forEach(builder::queryParam);
        }
        return builder.build();
      };
    }
  }
}
