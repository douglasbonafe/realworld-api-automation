package dev.dsbon.realworld.restclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dsbon.realworld.restclient.model.Dtos.ErrorResponse;
import org.springframework.http.HttpHeaders;

/**
 * A raw HTTP response plus on-demand deserialization.
 *
 * <p>The {@link ObjectMapper} is the one Spring Boot auto-configured — the same
 * instance the application would use in production — rather than a hand-built
 * mapper. If a Jackson module changes how dates or nulls are handled, the tests
 * change with the application instead of quietly disagreeing with it.
 */
public record ApiResponse(int status, String body, HttpHeaders headers, ObjectMapper mapper) {

  public <T> T as(Class<T> type) {
    try {
      return mapper.readValue(body, type);
    } catch (Exception e) {
      throw new AssertionError(
          "Could not read the response as %s.%nStatus: %d%nBody: %s"
              .formatted(type.getSimpleName(), status, truncate(body)),
          e);
    }
  }

  public ErrorResponse asError() {
    return as(ErrorResponse.class);
  }

  public boolean isSuccessful() {
    return status >= 200 && status < 300;
  }

  /**
   * The description used in assertion failure messages.
   *
   * <p>Including the body turns "expected 200 but was 422" into something you can
   * act on without re-running anything.
   */
  public String describe() {
    return "HTTP %d — %s".formatted(status, truncate(body));
  }

  private static String truncate(String value) {
    if (value == null || value.isEmpty()) {
      return "<empty>";
    }
    return value.length() <= 500 ? value : value.substring(0, 500) + "… (truncated)";
  }
}
