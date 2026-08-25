# Contract findings

Every divergence below was found by running requests against `https://api.realworld.show/api` and comparing the result with the service's own published OpenAPI document at `https://api.realworld.show/openapi.json` and with the [RealWorld API specification](https://realworld-docs.netlify.app/specifications/backend/endpoints/).

Each finding lists what the contract promises, what the service actually does, the request that demonstrates it, and what the test suite does about it.

This page is the reason the repository exists. A suite that only proves an API works is worth much less than one that tells you precisely where it does not match its own documentation.

---

## Summary

| # | Finding | Severity | In suite as |
|---|---|---|---|
| 1 | One global session for all callers | **Blocking** | Design constraint — no parallelism, `multi-user` excluded |
| 2 | `GET /articles/{slug}` requires a token | High | `contract-gaps` |
| 3 | `GET /articles/{slug}/comments` requires a token | High | `contract-gaps` |
| 4 | Duplicate usernames accepted (201, not 409) | High | `contract-gaps` |
| 5 | `?author=`, `?tag=`, `?favorited=` always return empty | High | `contract-gaps` |
| 6 | No ownership enforcement on articles or comments | High | `contract-gaps` + `multi-user` |
| 7 | Duplicate article title yields a `-N` slug, not 409 | Low | Documented; suite uses unique titles |
| 8 | OpenAPI `servers.url` double-prefixes `/api` | Low | Base URL is corrected in config |
| 9 | Token is not a JWT despite the security scheme saying so | Low | Documented |
| 10 | Comment `id` is a number, not a string | Low | Pinned by the DTO and JSON Schema |

---

## 1. The deployment keeps one global session

**Severity: blocking.** This is not a contract gap so much as a property of the sandbox, and it constrains the design of everything else.

Registering a user replaces the current identity for **every** caller, not just the one who registered.

```bash
A=$(curl -s -X POST https://api.realworld.show/api/users -H 'Content-Type: application/json' \
     -d '{"user":{"username":"alpha1","email":"alpha1@example.test","password":"Passw0rd!23"}}' \
     | jq -r .user.token)

B=$(curl -s -X POST https://api.realworld.show/api/users -H 'Content-Type: application/json' \
     -d '{"user":{"username":"bravo1","email":"bravo1@example.test","password":"Passw0rd!23"}}' \
     | jq -r .user.token)

echo "$A"; echo "$B"          # identical strings
curl -s https://api.realworld.show/api/user -H "Authorization: Token $A" | jq -r .user.username
# -> bravo1
```

Both registrations return the *same* token, and that token resolves to whoever registered last.

**What the suite does about it:**

- **Parallel execution is off everywhere** — `<forkCount>1</forkCount>` in the parent pom, `preserve-order="true"` with no `parallel` attribute in `testng.xml`, and `max-parallel: 1` in CI. Two tests running at once would authenticate as each other.
- Any test needing its own identity **registers at the start of its own method** and completes its work before another test registers.
- Scenarios needing two simultaneous users are tagged `multi-user` and excluded.
- CI runs the three modules **sequentially** rather than in parallel, for the same reason.

Against a self-hosted RealWorld backend none of this applies. That is why every module takes `realworld.base-url` as configuration, and why the excluded groups exist rather than the tests being deleted.

This finding also explains several others: findings 2, 3 and 6 are all downstream of the server resolving everything against a single current session.

---

## 2. `GET /articles/{slug}` requires authentication

**Contract:** the RealWorld specification lists this as a public endpoint. The service's own OpenAPI document lists responses `200` and `422` — no `401`, no `404`.

**Actual:** anonymous requests receive `404` with `{"errors":{"article":["not found"]}}`. The same request with any valid token returns `200`.

```bash
# with a token
curl -s -o /dev/null -w '%{http_code}\n' https://api.realworld.show/api/articles/$SLUG \
  -H "Authorization: Token $TOKEN"     # 200

# without
curl -s -o /dev/null -w '%{http_code}\n' https://api.realworld.show/api/articles/$SLUG   # 404
```

Note the status: `404`, not `401`. A client cannot distinguish "this article does not exist" from "you need to log in", which is worse than either answer alone.

**In the suite:** `ArticlesTest.readsAnArticleBySlug` reads authenticated, with a comment explaining why. `ContractGapsTest.articleShouldBeReadableAnonymously` asserts the documented behaviour and currently fails.

---

## 3. `GET /articles/{slug}/comments` requires authentication

Same shape as finding 2, same cause, same `404`-instead-of-`401` problem. Public in the specification; token-gated in practice.

**In the suite:** the lifecycle test reads comments authenticated; `ContractGapsTest.commentsShouldBeReadableAnonymously` asserts the documented behaviour.

---

## 4. Duplicate usernames are accepted

**Contract:** the OpenAPI document lists `409` among the responses for `POST /users`.

**Actual:** registering an existing username returns `201` and issues a token. Usernames are not unique.

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST https://api.realworld.show/api/users \
  -H 'Content-Type: application/json' \
  -d '{"user":{"username":"alpha1","email":"different@example.test","password":"Passw0rd!23"}}'
# -> 201
```

Since usernames are the public identifier for profiles and article authors, this is a correctness problem and not only a validation gap.

**In the suite:** `ContractGapsTest.duplicateUsernameShouldConflict`.

---

## 5. The list filters return nothing

`GET /articles` accepts `tag`, `author` and `favorited` query parameters. All three return `200` with an empty page, regardless of the data.

```bash
curl -s "https://api.realworld.show/api/articles?author=$USERNAME" -H "Authorization: Token $TOKEN"
# -> {"articles":[],"articlesCount":0}   even though $USERNAME has articles
```

An unfiltered `GET /articles?limit=3` works and returns real data, so this is specifically the filtering that is broken.

Worth noting *why* this matters more than it looks: the endpoint returns `200`, not `400`. A client cannot tell "no results" from "this filter is ignored", so a UI built on it silently shows an empty list forever.

**In the suite:** `ContractGapsTest.authorFilterShouldReturnResults`.

---

## 6. No ownership enforcement

A caller can update or delete an article they did not write, and delete a comment they did not post. Both return success rather than `403`.

This one carries a caveat that has to be stated plainly: **it cannot be proven on the public sandbox**, because finding #1 means the "other" user *is* the current user by the time the second request goes out. What is observable is that no `403` ever appears; what is not observable here is whether a spec-compliant deployment would produce one.

**In the suite:** `ContractGapsTest.ownershipShouldBeEnforced`, tagged both `contract-gaps` and `multi-user`, with the limitation written into the assertion message. Meaningful only against a self-hosted backend.

---

## 7. Duplicate article titles get a numbered slug

**Contract:** the OpenAPI document lists `409` among the responses for `POST /articles`.

**Actual:** `201`, with a numeric suffix appended to the slug.

```
POST /articles  {"title": "Same title"}   -> slug "same-title"
POST /articles  {"title": "Same title"}   -> slug "same-title-1"
```

This is arguably the *better* behaviour — it is what most blogging platforms do — but it is not what the document says. Low severity, and it does not break anything, so it is documented rather than asserted.

**In the suite:** not asserted. Every test uses a unique title, so it never encounters the case.

---

## 8. The OpenAPI document double-prefixes `/api`

The document declares:

```json
"servers": [{ "url": "https://api.realworld.show/api" }],
"paths":   { "/api/users": { … }, "/api/tags": { … } }
```

Concatenating those gives `https://api.realworld.show/api/api/tags`, which returns `404`. The live service answers on `https://api.realworld.show/api/tags`.

Anyone generating a client from this document gets one that cannot reach the API at all — which makes this the cheapest, highest-impact finding on the page.

**In the suite:** the base URL is configured as `https://api.realworld.show/api` and paths are written without the prefix. Every module carries a comment at the point of configuration.

---

## 9. The token is not a JWT

The security scheme in the OpenAPI document says:

> A JWT token is generated by the API by either registering via /users or logging in via /users/login.

The tokens the service actually issues look like `token_7f69c7576c038bec9498302b642e8817` — an opaque 32-character hex string, not three base64 segments.

The header format itself *is* as documented: `Authorization: Token <value>`, not `Bearer`.

**In the suite:** documented, and every client carries a comment at the point where the header is set, because `Bearer` is the reflex and it does not work here.

---

## 10. Comment ids are numbers

`comment.id` is a JSON number:

```json
{"comment":{"id":9,"createdAt":"…","body":"…","author":{…}}}
```

Several RealWorld backend implementations return a string here, and the reference specification is ambiguous. Low severity, but exactly the kind of thing that breaks a generated client.

**In the suite:** pinned as `long` in the DTOs of both Java modules, and as `"type": "integer"` in `schemas/comment-response.json` for the REST Assured module.

---

## What is *not* broken

Worth stating, because a findings page that lists only problems gives a misleading picture. These all behave exactly as documented:

- Registration, login, `GET /user`, `PUT /user` — including partial updates that correctly leave unmentioned fields alone
- Article create / read / update / delete, and the slug derivation
- Favourite and unfavourite, with the counter moving correctly in both directions
- Comment create / list / delete
- `GET /tags`
- `GET /articles/feed` correctly requiring a token
- The error envelope: `{"errors": {"field": ["message"]}}` at `401`, `404` and `422` alike — genuinely consistent, which is rarer than it should be
- HTTP status codes on the happy paths: `201` on create, `200` on update, `204` on delete

---

## Reproducing all of this

```bash
BASE=https://api.realworld.show/api

# 1. one global session
A=$(curl -s -X POST $BASE/users -H 'Content-Type: application/json' \
     -d '{"user":{"username":"a'$RANDOM'","email":"a'$RANDOM'@x.test","password":"Passw0rd!23"}}' | jq -r .user.token)
B=$(curl -s -X POST $BASE/users -H 'Content-Type: application/json' \
     -d '{"user":{"username":"b'$RANDOM'","email":"b'$RANDOM'@x.test","password":"Passw0rd!23"}}' | jq -r .user.token)
[ "$A" = "$B" ] && echo "same token issued to two different users"

# 8. the document's own server URL
curl -s -o /dev/null -w 'double prefix -> %{http_code}\n' $BASE/api/tags
curl -s -o /dev/null -w 'correct      -> %{http_code}\n' $BASE/tags
```

Or run the excluded group, which encodes all of it as assertions:

```bash
mvn test -Dgroups=contract-gaps -pl testng-java-httpclient
```
