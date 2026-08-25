package dev.dsbon.realworld.api.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** One configured {@link ObjectMapper} for the whole module. */
public final class Json {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          // Unknown properties are tolerated: a server adding a field is a
          // backwards-compatible change and must not fail the suite. Missing
          // fields still fail, because the assertions read them.
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private Json() {}

  public static ObjectMapper mapper() {
    return MAPPER;
  }

  public static String write(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("Could not serialize " + value, e);
    }
  }
}
