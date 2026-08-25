#!/usr/bin/env bash
# ============================================================
# RealWorld API Automation — one-shot GitHub setup
#
# Run from your WSL terminal (where gh is authenticated):
#   cd /mnt/c/Users/dsbon/OneDrive/Documentos/Claude/Projects/Portfolio
#   bash realworld-api-automation/setup-github.sh
#
# Creates the repo, commits the project in reviewable slices, and pushes.
# Safe to re-run: it stops early if the repo already exists.
# ============================================================
set -euo pipefail

REPO_NAME="realworld-api-automation"
WIN_SRC="/mnt/c/Users/dsbon/OneDrive/Documentos/Claude/Projects/Portfolio/${REPO_NAME}"
WSL_DEST="$HOME/${REPO_NAME}"
GITHUB_USER="douglasbonafe"
VISIBILITY="${VISIBILITY:-public}"   # VISIBILITY=private bash setup-github.sh

echo "==> Checking gh auth..."
command -v gh >/dev/null || { echo "gh CLI not found. Install it, then: gh auth login"; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "Not logged in. Run: gh auth login"; exit 1; }

if gh repo view "${GITHUB_USER}/${REPO_NAME}" >/dev/null 2>&1; then
  echo "Repo ${GITHUB_USER}/${REPO_NAME} already exists. Nothing to do."
  echo "To push new work: cd ${WSL_DEST} && git add -A && git commit && git push"
  exit 0
fi

echo "==> Copying project from Windows to WSL home..."
rm -rf "$WSL_DEST"
cp -r "$WIN_SRC" "$WSL_DEST"
cd "$WSL_DEST"

rm -rf .git */target */test-output

# Stale duplicate left behind because the authoring tool could not delete files
# in the source directory. The real Spring Boot application class for the
# REST Assured module is one package up. See the file's own Javadoc.
rm -f springboot-restassured/src/test/java/dev/dsbon/realworld/restassured/support/RestAssuredTestApplication.java

echo "==> Initializing git repo..."
git init -b main
git config user.email "dsbonafe@gmail.com"
git config user.name "Douglas Bonafé"

echo "==> Commit 1/6: reactor and documentation"
git add pom.xml README.md .gitignore
git commit -m "build: add Maven reactor for the three API testing stacks

- parent owns the Java release, dependency versions and Surefire config,
  so 'same scenarios, same environment' holds by construction
- parallelism disabled everywhere: the target keeps one global session"

echo "==> Commit 2/6: contract findings and API documentation"
git add docs/
git commit -m "docs: document ten divergences between the API and its OpenAPI contract

- one global session shared by all callers (blocking constraint)
- GET /articles/{slug} and its comments require a token despite being
  documented as public
- duplicate usernames accepted, list filters always empty, no ownership checks
- OpenAPI servers.url double-prefixes /api, so generated clients cannot connect
- every finding carries a reproducible curl command"

echo "==> Commit 3/6: TestNG + java.net.http module"
git add testng-java-httpclient/
git commit -m "test(testng): add suite built on the JDK HttpClient

- typed client that never throws on non-2xx: status codes are assertions
- lazy deserialization so a failed request reports the status, not a
  Jackson stack trace
- contract-gaps and multi-user groups excluded in testng.xml"

echo "==> Commit 4/6: Spring Boot + JUnit + RestClient module"
git add springboot-junit-restclient/
git commit -m "test(restclient): add suite built on Spring's RestClient

- client is a Spring bean with @ConfigurationProperties configuration
- defaultStatusHandler disables the exception-on-4xx behaviour that makes
  most Spring-based API suites swallow their own assertions
- context cached across test classes by SpringExtension"

echo "==> Commit 5/6: Spring Boot + REST Assured module"
git add springboot-restassured/
git commit -m "test(rest-assured): add suite with JSON Schema validation

- reusable request and response specifications built as Spring beans
- draft-04 schemas validate types, required keys and nullability in one line
- logging of the full request and response, but only when a test fails"

echo "==> Commit 6/6: continuous integration"
git add -A
git diff --cached --quiet || git commit -m "ci: run the three modules sequentially against the live API

- reachability probe first, so a downed sandbox reads as one clear failure
- max-parallel: 1, because the target keeps one global session
- nightly schedule: the target is third-party and can drift with no commit here
- non-blocking contract-gaps job to detect when upstream fixes a finding"

echo "==> Creating ${VISIBILITY} repo and pushing..."
gh repo create "${GITHUB_USER}/${REPO_NAME}" \
  --"${VISIBILITY}" \
  --source=. \
  --description "One API suite, three Java stacks (TestNG/HttpClient, Spring RestClient, REST Assured) — and ten documented contract gaps" \
  --push

gh repo edit "${GITHUB_USER}/${REPO_NAME}" \
  --add-topic api-testing --add-topic rest-assured --add-topic testng \
  --add-topic spring-boot --add-topic contract-testing --add-topic qa || true

echo ""
echo "Done: https://github.com/${GITHUB_USER}/${REPO_NAME}"
echo "Next: bash add-realworld-projects-to-profile.sh   (adds both repos to the portfolio table)"
