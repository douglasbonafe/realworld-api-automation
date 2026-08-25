package dev.dsbon.realworld.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dsbon.realworld.api.client.ApiResponse;
import dev.dsbon.realworld.api.client.RealWorldClient;
import dev.dsbon.realworld.api.model.Dtos.NewUserRequest;
import dev.dsbon.realworld.api.model.Dtos.UserResponse;

/**
 * Shared setup for every test class in this module.
 *
 * <p>{@link #anonymous} is a client with no credentials — used for the tests that
 * assert an endpoint rejects unauthenticated access.
 *
 * <p>{@link #registerFreshUser()} registers a new account and returns a client
 * bound to its token. Read the warning on that method before using it: on the
 * public sandbox, registering is a <b>global</b> act.
 */
public abstract class BaseApiTest {

  protected final RealWorldClient anonymous = RealWorldClient.anonymous(ApiConfig.BASE_URL);

  /**
   * Register a new account and return an authenticated client for it.
   *
   * <p><b>On api.realworld.show this changes the identity of every other caller.</b>
   * The sandbox keeps a single global session: register A, then register B, and
   * {@code GET /user} with A's token returns B. Consequences the whole suite is
   * built around:
   *
   * <ul>
   *   <li>Tests must not run in parallel — enforced in the parent pom.
   *   <li>Any test that needs its own identity must register at the <i>start</i>
   *       of its own method and finish its work before another test registers.
   *   <li>Scenarios that need two simultaneous users (following, ownership
   *       checks) are impossible here and are tagged {@code multi-user}, excluded
   *       from the default run.
   * </ul>
   *
   * <p>Against a self-hosted RealWorld backend none of this applies, and those
   * groups can be enabled. See docs/contract-findings.md, finding #1.
   */
  protected AuthenticatedUser registerFreshUser() {
    String username = TestData.username();
    String email = TestData.email(username);
    String password = TestData.password();

    ApiResponse response =
        anonymous.register(NewUserRequest.of(username, email, password));

    assertThat(response.status())
        .as("Registration must succeed before a test can use the account: %s", response.describe())
        .isEqualTo(201);

    UserResponse body = response.as(UserResponse.class);
    return new AuthenticatedUser(
        username, email, password, body.user().token(), anonymous.withToken(body.user().token()));
  }

  /** A registered account plus a client already bound to its token. */
  public record AuthenticatedUser(
      String username, String email, String password, String token, RealWorldClient client) {}
}
