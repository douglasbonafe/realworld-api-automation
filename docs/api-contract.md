# The API contract, as observed

The RealWorld API as `api.realworld.show` actually implements it. Every status code, header and body shape here was observed against the live service, not copied from the specification — and in ten places the two disagree ([docs/contract-findings.md](contract-findings.md)).

- **Base URL:** `https://api.realworld.show/api`
- **Published document:** `https://api.realworld.show/openapi.json` ([ReDoc](https://api.realworld.show/redoc))
- **Content type:** `application/json` on every request and response

---

## Authentication

```
Authorization: Token <value>
```

Not `Bearer`. And despite the security scheme in the OpenAPI document describing a JWT, the value is an opaque hex string:

```
token_7f69c7576c038bec9498302b642e8817
```

A token is issued by `POST /users` (register) and `POST /users/login`.

> **The deployment keeps one global session.** Registering a user replaces the current identity for every caller. See [finding #1](contract-findings.md#1-the-deployment-keeps-one-global-session) — it is the reason nothing in this repository runs in parallel.

---

## Endpoints

Auth column: **—** public, **token** requires authentication, **token\*** documented as public but requiring a token in practice.

### Users

| Method | Path | Auth | Success | Notes |
|---|---|---|---|---|
| `POST` | `/users` | — | `201` | Duplicate usernames accepted ([#4](contract-findings.md#4-duplicate-usernames-are-accepted)) |
| `POST` | `/users/login` | — | `200` | `401` on bad credentials |
| `GET` | `/user` | token | `200` | `401` with no or invalid token |
| `PUT` | `/user` | token | `200` | Partial: unsent fields are preserved |

### Profiles

| Method | Path | Auth | Success | Notes |
|---|---|---|---|---|
| `GET` | `/profiles/{username}` | token | `200` | Only resolves the **current** user; `404` otherwise |
| `POST` | `/profiles/{username}/follow` | token | — | Always `404` on this deployment ([#1](contract-findings.md#1-the-deployment-keeps-one-global-session)) |
| `DELETE` | `/profiles/{username}/follow` | token | — | Same |

### Articles

| Method | Path | Auth | Success | Notes |
|---|---|---|---|---|
| `GET` | `/articles` | — | `200` | `limit`/`offset` work; `tag`/`author`/`favorited` return empty ([#5](contract-findings.md#5-the-list-filters-return-nothing)) |
| `POST` | `/articles` | token | `201` | Slug derived from title; duplicates get `-N` ([#7](contract-findings.md#7-duplicate-article-titles-get-a-numbered-slug)) |
| `GET` | `/articles/feed` | token | `200` | `401` anonymous — correct |
| `GET` | `/articles/{slug}` | token\* | `200` | `404` anonymous ([#2](contract-findings.md#2-get-articlesslug-requires-authentication)) |
| `PUT` | `/articles/{slug}` | token | `200` | Partial; no ownership check ([#6](contract-findings.md#6-no-ownership-enforcement)) |
| `DELETE` | `/articles/{slug}` | token | `204` | No body |
| `POST` | `/articles/{slug}/favorite` | token | `200` | Returns the article with the counter updated |
| `DELETE` | `/articles/{slug}/favorite` | token | `200` | Counter decremented |

### Comments

| Method | Path | Auth | Success | Notes |
|---|---|---|---|---|
| `GET` | `/articles/{slug}/comments` | token\* | `200` | `404` anonymous ([#3](contract-findings.md#3-get-articlesslugcomments-requires-authentication)) |
| `POST` | `/articles/{slug}/comments` | token | `201` | `id` is a **number** ([#10](contract-findings.md#10-comment-ids-are-numbers)) |
| `DELETE` | `/articles/{slug}/comments/{id}` | token | `204` | No ownership check |

### Tags

| Method | Path | Auth | Success |
|---|---|---|---|
| `GET` | `/tags` | — | `200` |

Genuinely public, which is why the suite uses it as its connectivity probe.

---

## Response shapes

Every resource is wrapped in a single named key — never returned bare.

**User** — `POST /users`, `POST /users/login`, `GET /user`, `PUT /user`
```json
{"user": {"email": "…", "token": "token_…", "username": "…", "bio": null, "image": null}}
```
`bio` and `image` are `null` for a new account, not absent and not `""`.

**Article** — `POST /articles`, `GET /articles/{slug}`, `PUT`, favourite endpoints
```json
{"article": {
  "slug": "my-title", "title": "My title", "description": "…", "body": "…",
  "tagList": ["qa"], "createdAt": "2026-08-25T01:29:49.457Z", "updatedAt": "…",
  "favorited": false, "favoritesCount": 0,
  "author": {"username": "…", "bio": null, "image": null, "following": false}
}}
```

**Article list** — `GET /articles`, `GET /articles/feed`
```json
{"articles": [ … ], "articlesCount": 4}
```
`articlesCount` is the total matching the query, not the page size.

**Comment** — `POST /articles/{slug}/comments`
```json
{"comment": {"id": 9, "createdAt": "…", "updatedAt": "…", "body": "…", "author": { … }}}
```

**Tags** — `GET /tags`
```json
{"tags": ["ai", "api", "architecture", "backend", …]}
```

**Errors** — every failure, at every status
```json
{"errors": {"field": ["message", …]}}
```

The error envelope is genuinely consistent across `401`, `404` and `422`, which is rarer than it should be and worth pinning:

| Status | Example |
|---|---|
| `401` no token | `{"errors": {"token": ["is missing"]}}` |
| `401` bad login | `{"errors": {"credentials": ["invalid"]}}` |
| `404` missing article | `{"errors": {"article": ["not found"]}}` |
| `404` missing profile | `{"errors": {"profile": ["not found"]}}` |
| `422` validation | `{"errors": {"email": ["can't be blank"], "password": ["can't be blank"]}}` |

`schemas/errors-response.json` in the REST Assured module asserts this shape generically: an `errors` object with at least one key, every value a non-empty array of strings.

---

## Slug derivation

Reverse-engineered from the live service, and it is not the obvious rule:

```
"Slug_With Under_scores 1"  ->  "slug-with-under-scores-1"
"MiXeD Case! Punct? 2"      ->  "mixed-case--punct--2"
"  padded  spaces  3  "     ->  "padded--spaces--3"
"Accented Café 4"           ->  "accented-café-4"
```

1. Strip surrounding whitespace
2. Lowercase
3. Replace **each** character that is not a Unicode letter or digit with **one** hyphen

Runs are **not** collapsed, and accented letters **survive**. Both facts rule out the `[^a-z0-9]+` regex that is the first thing anyone writes.

A duplicate title produces `same-title-1`, `same-title-2`, and so on.

`TestData.slugFor` in each module implements this:

```java
title.strip().toLowerCase().replaceAll("[^\\p{L}\\p{N}]", "-")
```

---

## Re-verifying this page

```bash
BASE=https://api.realworld.show/api

# the published document
curl -s $BASE/../openapi.json | jq '.paths | keys'

# a full round trip
U=probe$RANDOM
T=$(curl -s -X POST $BASE/users -H 'Content-Type: application/json' \
    -d "{\"user\":{\"username\":\"$U\",\"email\":\"$U@x.test\",\"password\":\"Passw0rd!23\"}}" \
    | jq -r .user.token)

curl -s $BASE/user -H "Authorization: Token $T" | jq
curl -s -X POST $BASE/articles -H "Authorization: Token $T" -H 'Content-Type: application/json' \
    -d '{"article":{"title":"Probe '"$RANDOM"'","description":"d","body":"b","tagList":["qa"]}}' | jq
```
