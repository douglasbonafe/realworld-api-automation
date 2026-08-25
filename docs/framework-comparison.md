# Plain Java vs Spring RestClient vs REST Assured

Written after implementing the same twenty-odd scenarios three times against the same live API. Where the modules differ, the difference is the stack — the scenarios, assertions, test data strategy and target are identical by construction.

---

## The one-paragraph version

**REST Assured** is the right default for a suite whose only job is testing APIs: the smallest amount of infrastructure to write, the best failure output for free, and JSON Schema validation nothing else here can match. **Spring Boot + RestClient** wins when the client is also production code — you get typed configuration, dependency injection and one HTTP client shared between the app and its tests. **Plain `java.net.http`** is a genuinely reasonable choice that gets dismissed too fast: four dependencies, no magic, nothing between you and the wire, and it will still be compiling unchanged in ten years.

---

## Measured

Lines excluding comments and blank lines, for the same scenarios:

| | Test classes | Client + support | Total |
|---|---:|---:|---:|
| Plain Java + TestNG | 412 | 387 | **799** |
| Spring + JUnit + RestClient | 393 | 371 | **764** |
| Spring + REST Assured | 527 | **155** | **682** |

The shape of that table is the whole finding, and it is not what people expect.

REST Assured has the **longest test classes** — a fluent `given/when/then` chain spreads across more lines than a method call plus an AssertJ assertion — and by far the **smallest infrastructure**: no client to write, no DTOs, no response wrapper, no lazy-deserialization helper. 155 lines against ~380. Net, it still wins on total.

The two typed modules land within 5% of each other. Spring's saving is real but modest: it removes hand-rolled configuration reading and the `HttpClient` wiring, and replaces it with annotations.

**Counted with:**
```bash
find <module>/src -name "*.java" | xargs cat | grep -vE '^\s*($|//|/\*|\*|\*/)' | wc -l
```

---

## The decision that matters most: non-2xx handling

In application code, throwing on a `401` is correct. In a test suite it is backwards — the status code is usually *the assertion*. "`GET /user` without a token returns 401" is a requirement, not an error.

Each stack needs a different move:

```java
// java.net.http — already the behaviour. Nothing to configure.
HttpResponse<String> response = HTTP.send(request, BodyHandlers.ofString());
return new ApiResponse(response.statusCode(), response.body(), …);
```

```java
// Spring RestClient — throws HttpClientErrorException on 4xx by default.
// This is the single most important line in that module:
.defaultStatusHandler(status -> true, (request, response) -> {})
```

```java
// REST Assured — never throws. The status is just another assertion.
.then().statusCode(401)
```

Getting this wrong is why so many Java API suites are full of `try/catch` blocks that swallow the very thing being verified. Spring is the one that needs the deliberate act, and it is the one most often gotten wrong.

---

## Assertions, side by side

Create an article, check the derived slug and the author:

**Plain Java + TestNG**
```java
ApiResponse response = user.client()
    .createArticle(NewArticleRequest.of(title, "A description", "A body", tags()));

assertThat(response.status()).as(response.describe()).isEqualTo(201);

var article = response.as(ArticleResponse.class).article();
assertThat(article.slug()).isEqualTo(TestData.slugFor(title));
assertThat(article.tagList()).containsExactlyInAnyOrderElementsOf(tags());
assertThat(article.favoritesCount()).isZero();
assertThat(article.author().username()).isEqualTo(user.username());
```

**Spring Boot + REST Assured**
```java
given().spec(authenticated(user.token()))
    .body(TestData.newArticle(title))
.when()
    .post("/articles")
.then()
    .spec(createdJson)
    .body(matchesJsonSchemaInClasspath("schemas/article-response.json"))
    .body("article.slug", equalTo(TestData.slugFor(title)))
    .body("article.tagList", hasItems("qa", "automation"))
    .body("article.favoritesCount", equalTo(0))
    .body("article.author.username", equalTo(user.username()));
```

The trade is visible in one line. REST Assured reads as HTTP, which is what the test is about — but `"article.author.username"` is a **string**. A typo there is a runtime failure that says "expected X but was null", and no IDE will rename it when the field changes. The typed modules make the same mistake a compile error.

Which matters more depends on a question about your team, not about the tools: is the schema stable and the suite large (favour the DSL), or is the API evolving alongside code you also own (favour types)?

---

## What only REST Assured can do here

```java
.body(matchesJsonSchemaInClasspath("schemas/article-response.json"))
```

One line validating every field's type, every required key and every nullability rule across the whole response. The typed modules cannot replicate this cheaply, and — importantly — **Jackson will not do it for you**: `FAIL_ON_UNKNOWN_PROPERTIES` is disabled on purpose (a server adding a field is backwards compatible), and a missing field deserializes to `null` rather than throwing. Catching a type change requires an explicit assertion per field.

Two caveats, both worth knowing before you reach for it:

- The validator is backed by `com.github.java-json-tools`, which implements **JSON Schema draft-04**. Not draft-07, not 2020-12. A schema copied from a modern generator will have keywords silently ignored.
- It pulls in Groovy transitively, which is also what powers the GPath expressions. That is a real footprint increase over `java.net.http` + Jackson.

The other REST Assured capability worth naming is GPath, which evaluates *inside* the JSON document with no deserialization at all:

```java
.body("articles.size()", lessThanOrEqualTo(3))
.body("comments.body", hasItem(expectedBody))   // collect one field across an array
```

Expressing that second line in the typed modules means deserializing the list and streaming it.

---

## What Spring buys, concretely

Not HTTP — `java.net.http` already does HTTP, and Spring's `RestClient` sits on top of it in this very module. Spring earns its place through three things:

**Typed, layered configuration.** The plain module reads `System.getProperty` by hand. Here it is a record bound at context startup, with defaults, IDE autocompletion via the configuration processor, and override precedence (property → environment → `@TestPropertySource`) that already works the way everyone expects.

**Dependency injection.** Swapping the transport, adding a logging interceptor or pointing at another environment is a change to configuration, not to any test. The plain module would need every construction site updated.

**A shared client with production.** This is the real argument. If the same `RealWorldApi` bean is what your application uses to call the API, then the tests exercise the actual client — the actual serialization, the actual timeouts, the actual error handling. The other two modules test the API through a client that only exists for testing, which is a subtly different thing.

The cost is context startup, roughly two seconds, paid once because `SpringExtension` caches the context across every test class with the same configuration.

**RestClient specifically** (Spring Framework 6.1+) is the synchronous successor to `RestTemplate`: same blocking semantics, `WebClient`'s fluent API, no reactive types. For a test suite that is exactly right — a test that has to compose reactive streams to assert a status code is a test nobody maintains.

---

## TestNG vs JUnit

Barely a factor at this scale, but the differences that showed up:

**TestNG's groups are the more expressive model.** `@Test(groups = {"contract-gaps", "multi-user"})` plus include/exclude rules in `testng.xml` gives a declarative, versioned suite definition. JUnit's `@Tag` plus Surefire's `<excludedGroups>` reaches the same place, but the configuration is split between the test source and the build file, and running an excluded group means clearing the exclusion *and* setting the inclusion:

```bash
# TestNG
mvn test -Dgroups=contract-gaps

# JUnit
mvn test -Dtest.excluded.groups= -Dgroups=contract-gaps
```

**JUnit's ecosystem is where the momentum is.** Spring Boot's test support is JUnit-first, `@Nested` has no clean TestNG equivalent, and the extension model is better documented. For a Spring module, JUnit is the path of least resistance by a wide margin.

**Neither module uses parameterized tests much**, because the API scenarios are not naturally table-driven. Where they would be, JUnit 5's `@ParameterizedTest` is the more pleasant of the two.

---

## Feature matrix

| | Plain Java + TestNG | Spring + JUnit + RestClient | Spring + REST Assured |
|---|---|---|---|
| Direct dependencies | 4 | Spring Boot + JUnit | Spring Boot + REST Assured |
| Transitive footprint | tiny | large | large (+ Groovy) |
| Startup cost | none | ~2s context, cached | ~2s context, cached |
| Type-safe payloads | ✅ | ✅ | ❌ (maps) |
| Type-safe assertions | ✅ | ✅ | ❌ (GPath strings) |
| Typed configuration | manual | ✅ | ✅ |
| Dependency injection | ❌ | ✅ | ✅ |
| JSON Schema validation | manual | manual | ✅ one line |
| Failure diagnostics | what you build | what you build | ✅ automatic |
| Reusable request specs | hand-rolled | bean | ✅ built in |
| Query into JSON without deserializing | ❌ | ❌ | ✅ GPath |
| Shares a client with production | ❌ | ✅ | ❌ |
| Reads like | Java | Java | HTTP |

---

## Choosing

**REST Assured** if the repository's job is testing APIs and nothing else. Least infrastructure, best failure output for free, schema validation in one line. Accept that your assertions are strings.

**Spring Boot + RestClient** if the API client is production code you also own, or if the team already runs on Spring and typed configuration and DI are the ambient way things are done. The suite then tests the real client rather than a test-only replica.

**Plain `java.net.http` + TestNG** if dependencies are a cost you actually pay — a library, an air-gapped build, a security review that counts transitive artifacts — or if you want a suite with genuinely nothing to learn. Four dependencies, no magic, and every line of request-building visible.

**What does not vary:** all three produced clear, maintainable, non-flaky tests. The framework matters much less than the four things this repository holds constant — deterministic test data, a client that treats status codes as data, lazy deserialization, and failure messages that carry the response body. Get those right and any of these stacks will serve you; get them wrong and none of them will save you.
