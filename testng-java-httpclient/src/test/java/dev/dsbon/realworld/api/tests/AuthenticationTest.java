package dev.dsbon.realworld.api.tests;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dsbon.realworld.api.client.ApiResponse;
import dev.dsbon.realworld.api.model.Dtos.LoginRequest;
import dev.dsbon.realworld.api.model.Dtos.NewUser;
import dev.dsbon.realworld.api.model.Dtos.NewUserRequest;
import dev.dsbon.realworld.api.model.Dtos.UpdateUserRequest;
import dev.dsbon.realworld.api.model.Dtos.UserResponse;
import dev.dsbon.realworld.api.support.BaseApiTest;
import dev.dsbon.realworld.api.support.TestData;
import org.testng.annotations.Test;

/** Registration, login, and the current-user endpoints. */
public class AuthenticationTest extends BaseApiTest {

  @Test(groups = "smoke", description = "Registering returns 201 and a usable token")
  public void registersANewUser() {
    String username = TestData.username();
    String email = TestData.email(username);

    ApiResponse response =
        anonymous.register(NewUserRequest.of(username, email, TestData.password()));

    assertThat(response.status()).as(response.describe()).isEqualTo(201);

    var user = response.as(UserResponse.class).user();
    assertThat(user.username()).isEqualTo(username);
    assertThat(user.email()).isEqualTo(email);
    assertThat(user.token()).isNotBlank();
    // A fresh account has no profile content yet. Asserting the nulls pins the
    // shape of the response, so a server that starts omitting the keys entirely
    // is caught rather than silently tolerated.
    assertThat(user.bio()).isNull();
    assertThat(user.image()).isNull();
  }

  @Test(groups = "contract", description = "A registration missing required fields returns 422")
  public void rejectsIncompleteRegistration() {
    ApiResponse response =
        anonymous.register(new NewUserRequest(new NewUser(TestData.username(), null, null)));

    assertThat(response.status()).as(response.describe()).isEqualTo(422);

    // Errors always arrive as {"errors": {"field": ["message"]}}. Asserting the
    // *shape* matters as much as the status: a client that renders field-level
    // validation depends on it.
    assertThat(response.asError().errors())
        .containsKeys("email", "password")
        .allSatisfy((field, messages) -> assertThat(messages).isNotEmpty());
  }

  @Test(groups = "smoke", description = "Logging in with valid credentials returns 200 and a token")
  public void logsInWithValidCredentials() {
    var user = registerFreshUser();

    ApiResponse response = anonymous.login(LoginRequest.of(user.email(), user.password()));

    assertThat(response.status()).as(response.describe()).isEqualTo(200);
    assertThat(response.as(UserResponse.class).user().token()).isNotBlank();
  }

  @Test(groups = "contract", description = "Wrong credentials return 401, not 404 or 422")
  public void rejectsWrongCredentials() {
    var user = registerFreshUser();

    ApiResponse response =
        anonymous.login(LoginRequest.of(user.email(), "definitely-not-the-password"));

    assertThat(response.status()).as(response.describe()).isEqualTo(401);
    // One generic key for a failed login — the API does not distinguish "no such
    // account" from "wrong password", which is the correct enumeration defence.
    assertThat(response.asError().errors()).containsKey("credentials");
  }

  @Test(groups = "contract", description = "An unknown email returns the same 401 as a wrong password")
  public void rejectsUnknownAccountIdentically() {
    ApiResponse response =
        anonymous.login(LoginRequest.of("no-such-account-" + System.nanoTime() + "@example.test", "x"));

    assertThat(response.status()).as(response.describe()).isEqualTo(401);
    assertThat(response.asError().errors()).containsKey("credentials");
  }

  @Test(groups = "smoke", description = "GET /user without a token returns 401")
  public void rejectsAnonymousCurrentUser() {
    ApiResponse response = anonymous.currentUser();

    assertThat(response.status()).as(response.describe()).isEqualTo(401);
    assertThat(response.asError().errors()).containsKey("token");
  }

  @Test(groups = "contract", description = "A malformed token is rejected the same way as no token")
  public void rejectsGarbageToken() {
    ApiResponse response = anonymous.withToken("token_not_a_real_token").currentUser();

    assertThat(response.status()).as(response.describe()).isEqualTo(401);
  }

  @Test(groups = "smoke", description = "GET /user with a token returns the registered account")
  public void returnsTheCurrentUser() {
    var user = registerFreshUser();

    ApiResponse response = user.client().currentUser();

    assertThat(response.status()).as(response.describe()).isEqualTo(200);
    assertThat(response.as(UserResponse.class).user().username()).isEqualTo(user.username());
  }

  @Test(groups = "contract", description = "PUT /user updates only the fields that were sent")
  public void updatesTheCurrentUser() {
    var user = registerFreshUser();
    String bio = "Staff QA engineer — " + System.nanoTime();

    ApiResponse response = user.client().updateUser(UpdateUserRequest.bio(bio));

    assertThat(response.status()).as(response.describe()).isEqualTo(200);

    var updated = response.as(UserResponse.class).user();
    assertThat(updated.bio()).isEqualTo(bio);
    // The partial update must not clobber what it did not mention. This is the
    // half of the assertion that people forget, and the half that catches a
    // server rebuilding the record from a sparse payload.
    assertThat(updated.username()).isEqualTo(user.username());
    assertThat(updated.email()).isEqualTo(user.email());
  }
}
