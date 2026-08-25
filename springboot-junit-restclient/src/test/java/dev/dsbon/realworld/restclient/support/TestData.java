package dev.dsbon.realworld.restclient.support;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Unique test data.
 *
 * <p>No teardown anywhere in this suite, deliberately: the target is a shared
 * sandbox with no reset endpoint, so "clean up after yourself" is not on the
 * menu. Uniqueness buys the property teardown is usually chasing — a test that
 * cannot collide with a previous run, a parallel run, or a stranger using the
 * same public instance.
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
