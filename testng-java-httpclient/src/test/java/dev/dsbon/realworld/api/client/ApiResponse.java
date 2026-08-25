package dev.dsbon.realworld.api.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dsbon.realworld.api.model.Dtos.ErrorResponse;
import java.util.List;
import java.util.Map;

/**
 * A raw HTTP response plus the ability to deserialize it on demand.
 *
 * <p>The key design choice: the client <b>never</b> throws on a non-2xx status.
 * A 401 is not an exception in an API test — it is frequently the assertion. The
 * test decides what a status means; the client just reports it.
 *
 * <p>Deserialization is lazy for the same reason. A failing request has a body
 * that will not parse into the success type, and eagerly parsing would replace a
 * clear "expected 200, got 422" with an opaque Jackson stack trace.
 */
public record ApiResponse(int status, String body, Map<String, List<String>> headers) {

  private static final ObjectMapper MAPPER = Json.mapper();

  public <T> T as(Class<T> type) {
    try {
      return MAPPER.readValue(body, type);
    } catch (Exception e) {
      throw new AssertionError(
          "Could not read the response as %s.%nStatus: %d%nBody: %s"
              .formatted(type.getSimpleName(), status, truncate(body)),
          e);
    }
  }

  public <T> T as(TypeReference<T> type) {
    try {
      return MAPPER.readValue(body, type);
    } catch (Exception e) {
      throw new AssertionError(
          "Could not read the response as the requested type.%nStatus: %d%nBody: %s"
              .formatted(status, truncate(body)),
          e);
    }
  }

  /** Errors always arrive as {@code {"errors": {"field": ["message"]}}}. */
  public ErrorResponse asError() {
    return as(ErrorResponse.class);
  }

  public boolean isSuccessful() {
    return status >= 200 && status < 300;
  }

  /**
   * A description used in assertion failure messages.
   *
   * <p>Including the body is what turns "expected 200 but was 422" into something
   * you can act on without re-running anything.
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
