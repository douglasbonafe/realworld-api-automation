# RealWorld API Automation

> The same API test suite written three times — **plain Java + TestNG**, **Spring Boot + JUnit + RestClient**, and **Spring Boot + REST Assured** — against the same live API, with the same scenarios and the same assertions. Along the way it found ten places where the service disagrees with its own published OpenAPI contract.

Java API testing arguments usually stop at "REST Assured, obviously". This repository takes that seriously enough to test it: three stacks, one target, identical scenarios, and a written comparison of what each one actually costs and buys.

The target is [**api.realworld.show**](https://api.realworld.show/redoc) — the Conduit API from the [RealWorld](https://github.com/gothinkster/realworld) project, a Medium clone with users, articles, comments, favourites and tags. It publishes an OpenAPI document, which makes it a genuine contract-testing target rather than a toy.

It turned out to be a better target than expected, because **it does not honour its own contract**. Ten divergences are documented in [docs/contract-findings.md](docs/contract-findings.md), verified with reproducible `curl` commands and encoded as a separate, excluded test group. The headline one:

```bash
# Register two different users…
A=$(curl -s -X POST $BASE/users -d '{"user":{"username":"alpha1",...}}' | jq -r .user.token)
B=$(curl -s -X POST $BASE/users -d '{"user":{"username":"bravo1",...}}' | jq -r .user.token)

[ "$A" = "$B" ] && echo "…and they get the SAME token."

curl -s $BASE/user -H "Authorization: Token $A" | jq -r .user.username
# -> bravo1
```

The deployment keeps **one global session**. That single fact shaped the entire suite: no parallelism, no multi-user scenarios, and every test that needs an identity establishing it inside its own method.

---

## Table of contents

- [The three modules](#the-three-modules)
- [The scenarios](#the-scenarios)
- [Requirements](#requirements)
- [Running the suites](#running-the-suites)
- [Test groups, and why some tests are excluded](#test-groups-and-why-some-tests-are-excluded)
- [Pointing at your own backend](#pointing-at-your-own-backend)
- [Design decisions shared by all three](#design-decisions-shared-by-all-three)
- [What each stack looks like](#what-each-stack-looks-like)
- [Continuous integration](#continuous-integration)
- [Framework comparison](#framework-comparison)
- [Honest limits](#honest-limits)
- [Further reading](#further-reading)
- [License](#license)

---

## The three modules

```
realworld-api-automation/
├── pom.xml                          ← reactor: Java release, versions, no parallelism
├── testng-java-httpclient/          ← JDK HttpClient + Jackson + TestNG + AssertJ
├── springboot-junit-restclient/     ← Spring Boot 4 + JUnit + RestClient
├── springboot-restassured/          ← Spring Boot 4 + REST Assured + JSON Schema
└── docs/
    ├── contract-findings.md         ← the ten divergences (start here)
    ├── framework-comparison.md      ← the comparison, feature by feature
    ├── api-contract.md              ← the contract as observed, endpoint by endpoint
    └── architecture.md              ← patterns and reasoning
```

They are a Maven **reactor**, not a shared framework. Each module has its own client, its own model types and its own assertions — and that duplication is deliberate. Extracting the common parts into a shared module would erase exactly the differences the repository exists to compare, and the first time one stack needed an annotation the others did not, the shared module would sprout a conditional and stop being shared in any useful sense.

What the parent pom *does* own: the Java release, every dependency version, and the Surefire configuration. "Same scenarios, same environment" is guaranteed by construction rather than by discipline.

---

## The scenarios

All three modules implement the same twenty-odd cases:

| Area | Cases |
|---|---|
| **Authentication** | register (201 + token + response shape), incomplete registration (422 + field errors), login, wrong password (401, generic message), unknown account (identical 401), `GET /user` anonymous (401), malformed token (401), `GET /user` authenticated, `PUT /user` partial update |
| **Articles** | create (201 + derived slug + author + counters), create anonymous (401), read by slug, unknown slug (404), partial update, delete (204 + gone), favourite/unfavourite (flag + counter both directions), list with `limit`, pagination, feed requires auth |
| **Comments** | add (201 + author + numeric id), list contains it, delete (204), gone afterwards, anonymous comment (401) |
| **Tags** | public catalogue, non-empty, all entries non-blank |
| **Contract gaps** | six tests asserting the *documented* behaviour, excluded by default because the service does not honour it |

Three of these are worth calling out as more than checkbox coverage:

- **Partial updates assert what did *not* change.** `PUT /user {"bio": …}` must leave `username` and `email` alone. That is the half people forget, and the half that catches a server rebuilding a record from a sparse payload.
- **Deletes are followed by a read.** A `204` that leaves the resource retrievable is a real bug and only a second request finds it.
- **The slug is asserted against a rule, not against itself.** `TestData.slugFor` encodes the derivation, reverse-engineered from the live service. Reading the slug out of the response and asserting it equals itself would prove nothing.

That last one produced its own small finding. The rule is not what you would guess:

```
"Slug_With Under_scores 1"  ->  "slug-with-under-scores-1"
"MiXeD Case! Punct? 2"      ->  "mixed-case--punct--2"     // runs NOT collapsed
"  padded  spaces  3  "     ->  "padded--spaces--3"        // trimmed first
"Accented Café 4"           ->  "accented-café-4"          // letters survive
```

Strip, lowercase, replace **each** non-letter/non-digit with one hyphen. The `[^a-z0-9]+` regex almost everyone writes first is wrong twice over.

---

## Requirements

| | Version |
|---|---|
| JDK | 25 (any 17+ works — change `maven.compiler.release` in the parent pom) |
| Maven | 3.9+ |
| Network | Outbound HTTPS to `api.realworld.show`, or a self-hosted RealWorld backend |

No database, no containers, no fixtures to load. The suite creates everything it needs.

---

## Running the suites

```bash
mvn test                                    # all three modules, sequentially
mvn test -pl testng-java-httpclient         # one module
mvn test -pl springboot-restassured
```

Sequentially is not a typo — see [finding #1](docs/contract-findings.md#1-the-deployment-keeps-one-global-session). Parallelism against this target makes tests authenticate as each other.

---

## Test groups, and why some tests are excluded

| Group | Runs by default | What it is |
|---|---|---|
| `smoke` | Yes | The critical path. Enough to tell you the API is up and behaving. |
| `contract` | Yes | Full contract coverage: status codes, error shapes, edge cases. |
| `contract-gaps` | **No** | Tests that assert the *documented* behaviour, against a service that does not implement it. |
| `multi-user` | **No** | Scenarios needing two identities alive at once. Impossible on this sandbox. |

The `contract-gaps` group is the deliberate part. Those tests are not skipped because they are unreliable — **they are reliable, and they fail.** They are excluded so that a red build means a genuine regression rather than a permanent block of known defects, while still existing as executable, reviewable documentation of each gap.

Run them on purpose to see whether upstream has fixed anything:

```bash
mvn test -pl testng-java-httpclient -Dgroups=contract-gaps
mvn test -pl springboot-junit-restclient -Dtest.excluded.groups= -Dgroups=contract-gaps
mvn test -pl springboot-restassured     -Dtest.excluded.groups= -Dgroups=contract-gaps
```

CI runs this group nightly as a non-blocking job. A green result there is the signal to promote a test out of the excluded group.

---

## Pointing at your own backend

Every module reads one setting, so the whole repository moves with a single flag:

```bash
mvn test -Drealworld.base.url=http://localhost:8000/api
REALWORLD_BASE_URL=http://localhost:8000/api mvn test
```

Against a spec-compliant [RealWorld backend implementation](https://codebase.show/projects/realworld?category=backend) the excluded groups should pass, and parallelism becomes safe. That is the intended way to run the full suite; the public sandbox is the zero-setup default.

> **Mind the `/api` suffix.** The service's OpenAPI document declares `servers[0].url` as `https://api.realworld.show/api` *and* prefixes every path with `/api`, which concatenates to `/api/api/tags` and returns 404. The live service answers on `/api/tags`. Anyone generating a client straight from that document gets one that cannot reach the API — [finding #8](docs/contract-findings.md#8-the-openapi-document-double-prefixes-api).

---

## Design decisions shared by all three

**No client throws on a non-2xx.** In application code, an exception on `401` is right. In a test suite it is backwards: the status code is usually the assertion. `GET /user` without a token returning `401` is a *requirement*, not an error.

Each stack needs a different incantation to get there, and getting it wrong is why so many Java API suites are full of `try/catch` blocks that swallow the thing being verified:

```java
// java.net.http — the default already
return new ApiResponse(response.statusCode(), response.body(), …);

// Spring RestClient — throws by default; this turns it off
.defaultStatusHandler(status -> true, (request, response) -> {})

// REST Assured — never throws; the status is just another assertion
.then().statusCode(401)
```

**Deserialization is lazy.** A failing request has a body that will not parse into the success type. Eagerly deserializing replaces a clear "expected 200, got 422" with an opaque Jackson stack trace.

**Failure messages carry the body.** `assertThat(status).as(response.describe())` turns "expected 200 but was 422" into something actionable without re-running anything. REST Assured gets this for free via `enableLoggingOfRequestAndResponseIfValidationFails`.

**Unknown JSON properties are tolerated; missing ones are not.** A server *adding* a field is backwards compatible and must not break the suite. Removing one still fails, because the assertions read it.

**No teardown, unique data instead.** The target is a shared sandbox with no reset endpoint, so "clean up after yourself" is not available. Every write uses a timestamp-plus-counter value, which buys the property teardown is usually chasing: independence from previous runs, from parallel runs, and from strangers using the same public instance.

---

## What each stack looks like

The same assertion — create an article, check the derived slug — in all three:

**Plain Java + TestNG**
```java
ApiResponse response = user.client()
    .createArticle(NewArticleRequest.of(title, "A description", "A body", tags()));

assertThat(response.status()).as(response.describe()).isEqualTo(201);
var article = response.as(ArticleResponse.class).article();
assertThat(article.slug()).isEqualTo(TestData.slugFor(title));
assertThat(article.author().username()).isEqualTo(user.username());
```

**Spring Boot + JUnit + RestClient**
```java
ApiResponse response = user.session()
    .createArticle(NewArticleRequest.of(title, "A description", "A body", tags()));

assertThat(response.status()).as(response.describe()).isEqualTo(201);
var article = response.as(ArticleResponse.class).article();
assertThat(article.slug()).isEqualTo(TestData.slugFor(title));
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
    .body("article.tagList", hasItems("qa", "automation"));
```

That `matchesJsonSchemaInClasspath` line is the clearest single advantage in the repository: one line validates types, required keys and nullability across the whole response. The other two modules need an assertion per field, or they need Jackson to fail — which it will not, because tolerating unknown properties is exactly what you asked it to do.

---

## Continuous integration

`.github/workflows/ci.yml`, four jobs:

1. **`reachable`** — `GET /tags` must answer 200 before a build is spent. Failing here says "the sandbox is down", not forty confusing red tests.
2. **`build`** — a matrix over the three modules with `fail-fast: false` and **`max-parallel: 1`**. Never fail-fast: knowing that two implementations passed while one failed is the most useful signal this repository produces. Never parallel: the global-session problem applies across concurrent CI jobs too.
3. **`contract-gaps`** — the excluded group, `continue-on-error: true`. It never blocks a merge; it exists to answer "has upstream fixed anything?"
4. A **nightly schedule**, because the target is a third-party service. A repository with no commits would otherwise go quiet while its contract drifted underneath it.

---

## Framework comparison

Full write-up in **[docs/framework-comparison.md](docs/framework-comparison.md)**. Summary:

| | Plain Java + TestNG | Spring + JUnit + RestClient | Spring + REST Assured |
|---|---|---|---|
| Dependencies | 4 | Spring Boot + JUnit | Spring Boot + REST Assured (+ Groovy, transitively) |
| Lines for the same suite | most | middle | fewest |
| Startup cost | none | Spring context (~2s, cached) | Spring context (~2s, cached) |
| Type safety | full | full | none in the assertions |
| JSON Schema validation | manual | manual | **one line** |
| Failure diagnostics | what you build | what you build | **automatic, full request + response** |
| Reads like | Java | Java | a DSL |
| Best when | minimal dependencies, or a library you ship | the client is production code too | pure API test suites |

**One line each:** REST Assured is the right default for a suite that only tests APIs. RestClient wins when the same client is production code and you want typed configuration and DI. Plain `java.net.http` is a genuinely reasonable choice that people dismiss too quickly — four dependencies, no magic, and nothing between you and the wire.

---

## Honest limits

- **The suites have not been executed end-to-end in this repository's CI yet.** Every status code, error shape, header format, slug rule and JSON type asserted here was verified by hand against the live API before being written — the findings page carries the reproduction commands — but the first full run will be the first CI run. There is no JDK or Maven on the machine these were authored on, so compilation has not been verified either; expect to fix an import before you expect to fix an assertion.
- **The target is a third-party sandbox that can change or disappear.** The nightly job exists for exactly that reason. Point it at a self-hosted backend for anything that matters.
- **`contract-gaps` is expected to be red.** That is its purpose. Do not "fix" those tests by relaxing the assertions — the assertions are what document the gap.
- **Ownership enforcement cannot be proven here** ([finding #6](docs/contract-findings.md#6-no-ownership-enforcement)). The single global session means the "other" user is the current user. The test exists, tagged and excluded, and says so in its own failure message.
- **No performance, load, security-scan or fuzzing axis.** Those are separate disciplines with separate tools and would muddy a framework comparison.
- **Spring Boot 4.1.1 and Java 25 are current-and-recent rather than conservative.** Both are single properties in the parent pom if you need to drop back.

---

## Further reading

- [docs/contract-findings.md](docs/contract-findings.md) — the ten divergences, with reproductions
- [docs/api-contract.md](docs/api-contract.md) — the contract as observed, endpoint by endpoint
- [docs/framework-comparison.md](docs/framework-comparison.md) — the detailed comparison
- [docs/architecture.md](docs/architecture.md) — patterns and reasoning

## License

MIT
