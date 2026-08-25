package dev.dsbon.realworld.restclient.support;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dsbon.realworld.restclient.ApiResponse;
import dev.dsbon.realworld.restclient.RealWorldApi;
import dev.dsbon.realworld.restclient.model.Dtos.NewUserRequest;
import dev.dsbon.realworld.restclient.model.Dtos.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Base class for every test in this module.
 *
 * <p>{@code @SpringBootTest(webEnvironment = NONE)} boots the context once and
 * caches it for the whole run — {@link SpringExtension} keys the cache on the
 * context configuration, so all test classes here share a single startup. The
 * per-class cost of Spring is paid once, not four times.
 *
 * <p>{@link RealWorldApi} arrives by injection rather than construction, which is
 * the concrete benefit over the plain-Java module: swapping the transport,
 * adding a logging interceptor or pointing at another environment is a change to
 * configuration, not to any test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class BaseApiTest {

  @Autowired protected RealWorldApi api;

  protected RealWorldApi.Session anonymous;

  @BeforeEach
  void prepareAnonymousSession() {
    anonymous = api.anonymous();
  }

  /**
   * Register a new account and return a session bound to its token.
   *
   * <p><b>On api.realworld.show this changes the identity of every other caller.</b>
   * The sandbox keeps one global session: register A, then register B, and
   * {@code GET /user} with A's token returns B.
   *
   * <p>Consequences the suite is built around: no parallel execution (enforced in
   * the parent pom), any test needing its own identity registers at the start of
   * its own method, and scenarios requiring two simultaneous users are tagged
   * {@code multi-user} and excluded. See docs/contract-findings.md, finding #1.
   */
  protected AuthenticatedUser registerFreshUser() {
    String username = TestData.username();
    String email = TestData.email(username);
    String password = TestData.password();

    ApiResponse response = anonymous.register(NewUserRequest.of(username, email, password));

    assertThat(response.status())
        .as("Registration must succeed before a test can use the account: %s", response.describe())
        .isEqualTo(201);

    String token = response.as(UserResponse.class).user().token();
    return new AuthenticatedUser(username, email, password, token, api.as(token));
  }

  /** A registered account plus a session already bound to its token. */
  public record AuthenticatedUser(
      String username,
      String email,
      String password,
      String token,
      RealWorldApi.Session session) {}
}
