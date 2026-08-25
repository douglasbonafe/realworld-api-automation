package dev.dsbon.realworld.api.support;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Unique test data.
 *
 * <p>There is no teardown anywhere in this suite, and that is deliberate: the
 * target is a shared sandbox with no reset endpoint, so "clean up after
 * yourself" is not available. Uniqueness buys the property teardown is usually
 * chasing — a test that cannot collide with a previous run, a parallel run, or a
 * stranger using the same public instance.
 *
 * <p>The counter guards against the one case a timestamp alone does not: two
 * values generated inside the same millisecond.
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
    // Long enough to survive a future minimum-length rule without a rewrite.
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
   * The slug this API derives from a title.
   *
   * <p>The rule was reverse-engineered from the live service rather than assumed,
   * and it is not the obvious one:
   *
   * <pre>
   *   "Slug_With Under_scores 1"  ->  "slug-with-under-scores-1"
   *   "MiXeD Case! Punct? 2"      ->  "mixed-case--punct--2"     // NOT collapsed
   *   "  padded  spaces  3  "     ->  "padded--spaces--3"        // trimmed first
   *   "Accented Café 4"           ->  "accented-café-4"          // letters kept
   * </pre>
   *
   * So: strip surrounding whitespace, lowercase, and replace <b>each</b>
   * character that is not a Unicode letter or digit with a single hyphen. Runs
   * are <b>not</b> collapsed, and accented letters survive — which rules out the
   * {@code [^a-z0-9]+} regex almost everyone writes first.
   *
   * <p>Encoding the rule here rather than reading the slug back out of the
   * response is what makes "create an article" an assertion about the contract
   * instead of a tautology.
   */
  public static String slugFor(String title) {
    return title.strip().toLowerCase().replaceAll("[^\\p{L}\\p{N}]", "-");
  }

  private static String unique() {
    return System.currentTimeMillis() + "_" + COUNTER.incrementAndGet();
  }
}
