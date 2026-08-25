# Architecture

The patterns every module follows, and the reasoning behind each. Where a stack forced a different shape, that is called out rather than smoothed over.

---

## The reactor is not a framework

```
pom.xml                          ← Java release, dependency versions, no parallelism
├── testng-java-httpclient/      ← own client, own DTOs, own assertions
├── springboot-junit-restclient/ ← own client, own DTOs, own assertions
└── springboot-restassured/      ← own specs, own schemas, own assertions
```

The three modules duplicate their model records and their test-data helpers. That is deliberate.

Extracting the shared parts into a `common` module would couple three implementations that exist precisely to be compared. The first time REST Assured needed a Jackson annotation the others did not — or Spring wanted `@ConfigurationProperties` binding on a type the plain module constructs by hand — the shared module would sprout a conditional and stop being shared in any useful sense. Worse, a reader could no longer open one module and see the whole approach.

What the parent **does** own is everything that must not vary: the Java release, every dependency version, and the Surefire configuration. "Same scenarios, same environment" is then guaranteed by construction rather than by discipline.

---

## The layers

```
  test class          "creating an article returns 201 and the derived slug"
        │              scenario language, assertions on business outcomes
        ▼
  client / spec       createArticle(), given().spec(authenticated(token))
        │              one place that knows paths, headers and auth
        ▼
  transport           java.net.http | RestClient | REST Assured
```

The rule: **a test never builds a URL and never sets a header.** If a test needs a header, the client is missing a method. That is what makes "point the whole suite at a different backend" a configuration change rather than a pull request.

---

## Status codes are data, never exceptions

The single most consequential decision in the repository, and it recurs in every module:

```java
// java.net.http — already the behaviour
return new ApiResponse(response.statusCode(), response.body(), …);

// Spring RestClient — throws by default; explicitly disabled
.defaultStatusHandler(status -> true, (request, response) -> {})

// REST Assured — never throws
.then().statusCode(401)
```

In application code an exception on `401` is right. In a test suite it is backwards: `GET /user` without a token returning `401` is a requirement, not a failure. A client that throws forces every negative test into a `try/catch` that swallows what is being verified.

---

## Deserialization is lazy

`ApiResponse` holds the raw body and deserializes on demand:

```java
ApiResponse response = user.client().createArticle(request);
assertThat(response.status()).isEqualTo(201);            // status first
var article = response.as(ArticleResponse.class).article();  // then the body
```

Eagerly parsing into the success type turns a failing request — whose body is an error envelope — into an opaque Jackson stack trace instead of a clear "expected 201, got 422". The status assertion has to be able to run and fail *before* anything tries to read the body as an article.

---

## Failure messages carry the response

```java
assertThat(response.status()).as(response.describe()).isEqualTo(201);
```

`describe()` renders `HTTP 422 — {"errors":{"email":["can't be blank"]}}`, truncated at 500 characters. The difference between

```
expected: 201 but was: 422
```

and

```
HTTP 422 — {"errors":{"email":["can't be blank"]}}
expected: 201 but was: 422
```

is whether a CI failure needs a local reproduction. REST Assured gets the same effect for free through `enableLoggingOfRequestAndResponseIfValidationFails(LogDetail.ALL)` — a global switch, not a filter: `RequestLoggingFilter` logs unconditionally and buries a passing run in noise.

---

## Contract-testing posture on JSON

Response records are annotated:

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record Article(String slug, String title, …) {}
```

A server **adding** a field is a backwards-compatible change and must not break the suite. Removing one still fails, because the assertions read it. Failing on unknown properties would turn every additive API release into a red build, which teaches people to stop trusting the suite.

Request records use `@JsonInclude(NON_NULL)` so a partial update sends only the fields it means to change:

```java
UpdateUserRequest.bio("…")   // serializes to {"user":{"bio":"…"}}, nothing else
```

Without it, a sparse record would serialize nulls and the "partial update preserves other fields" test would be testing the wrong thing entirely.

---

## Test data: unique, never cleaned up

```java
public static String username() { return "qa_" + unique(); }
private static String unique()  { return System.currentTimeMillis() + "_" + COUNTER.incrementAndGet(); }
```

There is no teardown anywhere in this repository. The target is a shared public sandbox with no reset endpoint, so "clean up after yourself" is not on the menu.

Uniqueness buys the property teardown is usually chasing: independence from previous runs, from parallel runs, and from strangers hitting the same public instance. The counter covers the one case a timestamp alone does not — two values generated inside the same millisecond.

The cost is accumulating junk on a public sandbox. Against a self-hosted backend you would reset between runs instead, which is one more reason `realworld.base-url` is configuration.

---

## Assert derived values against the rule, not against themselves

```java
assertThat(article.slug()).isEqualTo(TestData.slugFor(title));
```

Reading the slug out of the response and asserting it equals itself proves nothing. `slugFor` encodes the derivation, reverse-engineered from the live service, so the assertion is about the contract.

Doing this properly also produced a small finding — the rule is not the obvious one:

```java
title.strip().toLowerCase().replaceAll("[^\\p{L}\\p{N}]", "-")
```

Runs are **not** collapsed and accented letters **survive**, so the `[^a-z0-9]+` regex everyone writes first is wrong twice over. That only surfaced because the assertion was written this way round.

---

## Groups, and a deliberately failing suite

| Group | Default | Purpose |
|---|---|---|
| `smoke` | run | Critical path. Enough to say the API is up and behaving. |
| `contract` | run | Full coverage: status codes, error shapes, edge cases. |
| `contract-gaps` | **excluded** | Asserts the *documented* behaviour against a service that does not implement it. |
| `multi-user` | **excluded** | Needs two identities alive at once. Impossible on the sandbox. |

The `contract-gaps` group is the part worth defending. Those tests are not skipped for being unreliable — **they are reliable, and they fail.**

Three options exist when an API does not match its documentation:

1. Delete the test. The knowledge is lost.
2. Assert the *actual* behaviour. The suite now enforces the bug, and the day it is fixed the suite goes red for the wrong reason.
3. Assert the *documented* behaviour and exclude the test.

Option 3 keeps the gap as executable, reviewable documentation, keeps the everyday build meaningful, and gives CI something concrete to check nightly. A green `contract-gaps` job is the signal to promote a test into the default suite.

Each of those tests carries the observed behaviour in its assertion message, so its failure output is a finding rather than a puzzle.

---

## Why nothing runs in parallel

`<forkCount>1</forkCount>` in the parent, `preserve-order="true"` with no `parallel` attribute in `testng.xml`, `max-parallel: 1` in CI.

None of that is caution. `api.realworld.show` keeps one global session — registering a user replaces the current identity for every caller, including other CI jobs hitting the same host. Two tests running concurrently authenticate as each other.

Verified: register A, register B, then `GET /user` with A's token returns B. [Finding #1](contract-findings.md#1-the-deployment-keeps-one-global-session).

All three frameworks parallelise happily; the constraint is the target. Against a self-hosted backend, remove the three settings above.

---

## What is deliberately absent

- **A shared `common` module.** See the top of this page.
- **Mocking or stubbing (WireMock, MockServer).** This is contract testing against a real deployment. A mock would test the mock.
- **Consumer-driven contract testing (Pact).** A different discipline with a different goal — verifying a *provider* against *consumer* expectations. Worth adding when there is a consumer to speak for; not what this repository is.
- **Performance and load testing.** Separate tools (k6, Gatling), separate concerns, and they would muddy a framework comparison.
- **Retries.** An API test that needs a retry to pass is telling you something, and hiding it is the wrong response. The one genuine flake source here — the shared global session — is solved by removing parallelism, not by retrying.
