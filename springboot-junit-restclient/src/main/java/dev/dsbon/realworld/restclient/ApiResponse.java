package dev.dsbon.realworld.restclient;

import dev.dsbon.realworld.restclient.model.Dtos.ErrorResponse;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.ObjectMapper;

/**
 * A raw HTTP response plus on-demand deserialization.
 *
 * <p>The {@link ObjectMapper} is the one Spring Boot auto-configured — the same
 * instance the application would use in production — rather than a hand-built
 * mapper. If a Jackson module changes how dates or nulls are handled, the tests
 * change with the application instead of quietly disagreeing with it.
 *
 * <p><b>Note the package: {@code tools.jackson.databind}, not {@code
 * com.fasterxml.jackson.databind}.</b> Spring Boot 4 moved to Jackson 3, which
 * relocated {@code jackson-core} and {@code jackson-databind} under {@code
 * tools.jackson.*}. Annotations did <i>not</i> move — {@code
 * com.fasterxml.jackson.annotation.JsonIgnoreProperties} and friends keep their
 * old coordinates, which is why {@code Dtos} still imports them from there.
 *
 * <p>This is the single most likely thing to break when migrating a Spring Boot 3
 * test suite to 4: the annotations compile, the mapper does not.
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
