package dev.dsbon.realworld.restclient.tests;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dsbon.realworld.restclient.ApiResponse;
import dev.dsbon.realworld.restclient.model.Dtos.LoginRequest;
import dev.dsbon.realworld.restclient.model.Dtos.NewUser;
import dev.dsbon.realworld.restclient.model.Dtos.NewUserRequest;
import dev.dsbon.realworld.restclient.model.Dtos.UpdateUserRequest;
import dev.dsbon.realworld.restclient.model.Dtos.UserResponse;
import dev.dsbon.realworld.restclient.support.BaseApiTest;
import dev.dsbon.realworld.restclient.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Authentication")
class AuthenticationTest extends BaseApiTest {

  @Test
  @Tag("smoke")
  @DisplayName("registering returns 201 and a usable token")
  void registersANewUser() {
    String username = TestData.username();
    String email = TestData.email(username);

    ApiResponse response =
        anonymous.register(NewUserRequest.of(username, email, TestData.password()));

    assertThat(response.status()).as(response.describe()).isEqualTo(201);

    var user = response.as(UserResponse.class).user();
    assertThat(user.username()).isEqualTo(username);
    assertThat(user.email()).isEqualTo(email);
    assertThat(user.token()).isNotBlank();
    // A fresh account has no profile content. Asserting the nulls pins the
    // response shape, so a server that stops emitting the keys is caught.
    assertThat(user.bio()).isNull();
    assertThat(user.image()).isNull();
  }

  @Test
  @Tag("contract")
  @DisplayName("a registration missing required fields returns 422 with field errors")
  void rejectsIncompleteRegistration() {
    ApiResponse response =
        anonymous.register(new NewUserRequest(new NewUser(TestData.username(), null, null)));

    assertThat(response.status()).as(response.describe()).isEqualTo(422);
    assertThat(response.asError().errors())
        .containsKeys("email", "password")
        .allSatisfy((field, messages) -> assertThat(messages).isNotEmpty());
  }

  @Test
  @Tag("smoke")
  @DisplayName("valid credentials return 200 and a token")
  void logsInWithValidCredentials() {
    var user = registerFreshUser();

    ApiResponse response = anonymous.login(LoginRequest.of(user.email(), user.password()));

    assertThat(response.status()).as(response.describe()).isEqualTo(200);
    assertThat(response.as(UserResponse.class).user().token()).isNotBlank();
  }

  @Test
  @Tag("contract")
  @DisplayName("wrong credentials return 401 with a generic message")
  void rejectsWrongCredentials() {
    var user = registerFreshUser();

    ApiResponse response = anonymous.login(LoginRequest.of(user.email(), "not-the-password"));

    assertThat(response.status()).as(response.describe()).isEqualTo(401);
    // The API does not distinguish "no such account" from "wrong password",
    // which is the correct enumeration defence — worth pinning.
    assertThat(response.asError().errors()).containsKey("credentials");
  }

  @Test
  @Tag("smoke")
  @DisplayName("GET /user without a token returns 401")
  void rejectsAnonymousCurrentUser() {
    ApiResponse response = anonymous.currentUser();

    assertThat(response.status()).as(response.describe()).isEqualTo(401);
    assertThat(response.asError().errors()).containsKey("token");
  }

  @Test
  @Tag("contract")
  @DisplayName("a malformed token is rejected like no token at all")
  void rejectsGarbageToken() {
    assertThat(api.as("token_not_a_real_token").currentUser().status()).isEqualTo(401);
  }

  @Test
  @Tag("smoke")
  @DisplayName("GET /user with a token returns the registered account")
  void returnsTheCurrentUser() {
    var user = registerFreshUser();

    ApiResponse response = user.session().currentUser();

    assertThat(response.status()).as(response.describe()).isEqualTo(200);
    assertThat(response.as(UserResponse.class).user().username()).isEqualTo(user.username());
  }

  @Test
  @Tag("contract")
  @DisplayName("PUT /user updates only the fields that were sent")
  void updatesTheCurrentUser() {
    var user = registerFreshUser();
    String bio = "Staff QA engineer — " + System.nanoTime();

    ApiResponse response = user.session().updateUser(UpdateUserRequest.bio(bio));

    assertThat(response.status()).as(response.describe()).isEqualTo(200);

    var updated = response.as(UserResponse.class).user();
    assertThat(updated.bio()).isEqualTo(bio);
    // The half of the assertion people forget: a partial update must not clobber
    // what it did not mention.
    assertThat(updated.username()).isEqualTo(user.username());
    assertThat(updated.email()).isEqualTo(user.email());
  }
}
