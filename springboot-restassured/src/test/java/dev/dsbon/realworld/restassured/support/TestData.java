package dev.dsbon.realworld.restassured.support;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Unique test data and request-body builders.
 *
 * <p>Bodies are {@link Map}s rather than typed records here, and that is the
 * point of the module: REST Assured serializes any object Jackson can handle, so
 * a map literal is often the shortest honest way to express a payload — including
 * a deliberately malformed one, which a typed record cannot represent at all.
 *
 * <p>The trade-off is real and worth naming: a typo in a key is a runtime 422
 * instead of a compile error. That is exactly the comparison this repository
 * exists to make concrete.
 */
public final class TestData {

  private static final AtomicLong COUNTER = new AtomicLong();

  private TestData() {}

  public static String username() {
    return "qa_" + unique();
  }

  public static String email(String username) {
    return username + "@example.test";
  }

  public static String password() {
    return "Passw0rd!23";
  }

  public static String articleTitle() {
    return "Contract test article " + unique();
  }

  public static String commentBody() {
    return "Automated comment " + unique();
  }

  public static List<String> tags() {
    return List.of("qa", "automation");
  }

  public static Map<String, Object> newArticle(String title) {
    return Map.of(
        "article",
        Map.of("title", title, "description", "A description", "body", "A body", "tagList", tags()));
  }

  public static Map<String, Object> comment(String body) {
    return Map.of("comment", Map.of("body", body));
  }

  public static Map<String, Object> login(String email, String password) {
    return Map.of("user", Map.of("email", email, "password", password));
  }

  /**
   * The slug this API derives from a title, reverse-engineered from the live
   * service:
   *
   * <pre>
   *   "Slug_With Under_scores 1"  ->  "slug-with-under-scores-1"
   *   "MiXeD Case! Punct? 2"      ->  "mixed-case--punct--2"     // NOT collapsed
   *   "  padded  spaces  3  "     ->  "padded--spaces--3"        // trimmed first
   *   "Accented Café 4"           ->  "accented-café-4"          // letters kept
   * </pre>
   *
   * Strip, lowercase, then replace <b>each</b> non-letter/non-digit with one
   * hyphen. Runs are not collapsed and accented letters survive, which rules out
   * the {@code [^a-z0-9]+} regex almost everyone reaches for first.
   */
  public static String slugFor(String title) {
    return title.strip().toLowerCase().replaceAll("[^\\p{L}\\p{N}]", "-");
  }

  private static String unique() {
    return System.currentTimeMillis() + "_" + COUNTER.incrementAndGet();
  }
}
