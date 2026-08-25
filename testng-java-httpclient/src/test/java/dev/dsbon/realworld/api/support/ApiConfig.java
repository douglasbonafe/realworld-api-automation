package dev.dsbon.realworld.api.support;

/**
 * The target under test.
 *
 * <p>System property first, then environment variable, then the public sandbox.
 * That order lets the same build run unchanged locally, in CI, and against a
 * self-hosted RealWorld backend:
 *
 * <pre>
 *   mvn test
 *   mvn test -Drealworld.base.url=http://localhost:8000/api
 *   REALWORLD_BASE_URL=http://localhost:8000/api mvn test
 * </pre>
 *
 * <p>Note the {@code /api} suffix in the default. The published OpenAPI document
 * declares {@code servers[0].url} as {@code https://api.realworld.show/api} while
 * also prefixing every path with {@code /api}, which would produce
 * {@code /api/api/tags}. The live service answers on {@code /api/tags}; the
 * document is wrong. See docs/contract-findings.md, finding #8.
 */
public final class ApiConfig {

  public static final String BASE_URL =
      resolve("realworld.base.url", "REALWORLD_BASE_URL", "https://api.realworld.show/api");

  private ApiConfig() {}

  private static String resolve(String property, String env, String fallback) {
    String fromProperty = System.getProperty(property);
    if (fromProperty != null && !fromProperty.isBlank()) {
      return fromProperty;
    }
    String fromEnv = System.getenv(env);
    if (fromEnv != null && !fromEnv.isBlank()) {
      return fromEnv;
    }
    return fallback;
  }
}
