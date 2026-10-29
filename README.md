# Template of empty project

To understand the Akka concepts that are the basis for this example, see [Development Process](https://doc.akka.io/concepts/development-process.html) in the documentation.

This project contains the skeleton to create an Akka service. To understand more about these components, see [Developing services](https://doc.akka.io/sdk/index.html).

You are supposed to change `empty-service` and the package name `com.example` to your own names.

Use Maven to build your project:

```shell
mvn compile
```

To start your service locally, run:

```shell
AGENT_MODEL_PROVIDER=litellm SPIFFE_ENABLED=false mvn compile exec:java
```

`application.conf` is the deployment config (what the Akka platform and the
standalone image load by default). `AGENT_MODEL_PROVIDER=litellm` routes the
default agent provider through a local `litellm` proxy instead of Vertex AI, so
no cloud API key is needed; leaving it out runs with Vertex AI, which needs
`VERTEX_AI_API_KEY` set.

Two things changed with Akka SDK 3.7.0:

- `SPIFFE_ENABLED=false` is needed for local runs. The runtime now enables SPIFFE
  workload identity by default and refuses to start without a trust domain, which
  only the Akka platform provides.
- `-Dconfig.resource=application-dev.conf` no longer starts the service: any
  `-Dconfig.resource` override bypasses the runtime's dev-mode persistence defaults
  and fails with "The Akka Persistence R2DBC database config scheme has changed".
  `application-dev.conf` (which `include`s `application.conf` and sets the same
  `litellm` override) is kept for reference by the container notes below.

You can use the [Akka Console](https://console.akka.io) to create a project and see the status of your service.

Build container image:

```shell
mvn clean install -DskipTests
```

Install the `akka` CLI as documented in [Install Akka CLI](https://doc.akka.io/reference/cli/index.html).

Deploy the service using the image tag from above `mvn install`:

```shell
akka service deploy empty-service empty-service:tag-name --push
```

Refer to [Deploy and manage services](https://doc.akka.io/operations/services/deploy-service.html) for more information.

## Running with Postgres

Entity, workflow and view state is persisted by the Akka runtime. `mvn compile exec:java`
and the tests use an in-memory store; a standalone run (container image or
`akka local cluster`) stores everything in Postgres. The connection is configured in
`application-dev.conf` under `akka.persistence.r2dbc` and read from `DB_HOST`, `DB_PORT`,
`DB_DATABASE`, `DB_USER` and `DB_PASSWORD` (defaults: `localhost:5432`, database, user and
password all `postgres`; set `DB_PASSWORD` for anything other than local development). Tables are created automatically.

A standalone container image loads `application.conf` by default, not
`application-dev.conf` - so as shipped, the `compose.yml` setup below does **not** pick up
the `akka.persistence.r2dbc` block above; `DB_HOST`/`DB_PASSWORD` etc. have no effect and the
container falls back to the Akka runtime's own r2dbc defaults. Passing
`-Dconfig.resource=application-dev.conf` to the container (e.g. via `JAVA_TOOL_OPTIONS`) would
pick it up, but also forces `model-provider` to `litellm` for that container (see
`application-dev.conf`), overriding the `AGENT_MODEL_PROVIDER=vertex-ai` / `VERTEX_AI_API_KEY`
that `compose.yml` already sets for it - so that's only a fix if you also want the standalone
container to talk to a local litellm proxy instead of Vertex AI.

```shell
mvn install -DskipTests -Pstandalone -Ddocker.base.image=eclipse-temurin:25-jre-jammy -Ddocker.platform=linux/arm64
SERVICE_IMAGE=<image built above> ANTHROPIC_API_KEY=... docker compose up
```

The base image override is needed because the parent pom's standalone profile defaults to a Java 21 JRE,
while this project compiles for Java 25. Maven also needs `DOCKER_HOST` set if you use a Docker context such as Colima.

To browse the stored data, open Adminer at <http://localhost:8081> (System `PostgreSQL`, server
`postgres-db`, user `postgres`, password `postgres` unless `DB_PASSWORD` is set, database
`postgres`). Events are in the `journal` table and snapshots in `snapshot`. Postgres is also
published on host port 5434 (override with `POSTGRES_HOST_PORT`) for `psql` or other clients.

On the Akka platform the database is managed for you, so none of this is needed there.

## Log sanitization sample

The service masks personal data in its **logs only**, using Akka's runtime
[data sanitization](https://doc.akka.io/sdk/sanitization.html) (SDK 3.7.0+).
`src/main/resources/application.conf` defines six entries under
`akka.javasdk.sanitization.sanitizers`, all with `apply-at = ["logs"]`, so nothing is
masked in agent model calls, tool results or ad-hoc sanitizer calls:

| Entry | What it masks |
|---|---|
| `log-email` | predefined `EMAIL` |
| `log-phone` | predefined `PHONE` |
| `log-credit-card` | predefined `CREDIT_CARD` |
| `log-iban` | predefined `IBAN` |
| `log-ip-address` | predefined `IP_ADDRESS` |
| `log-sensitive-fields` | custom `com.example.sanitizer.SensitiveFieldLogSanitizer`: the values of the configured `fields` (`password`, `ssn`, `dateOfBirth`, `securityAnswer`) in JSON (`"ssn":"..."`) and `key=value` text, keeping the field names visible |

Matched text is replaced by `*` of the same length. Output observed from a local run
(all sample data is synthetic):

| Kind | Logged line | In the logs |
|---|---|---|
| `email` | `Password reset requested for jane.doe@example.com` | `Password reset requested for ********************` |
| `phone` | `SMS verification sent to 202-555-0143` | `SMS verification sent to ************` |
| `credit-card` | `Payment declined for card 4111 1111 1111 1111` | `Payment declined for card *******************` |
| `iban` | `Refund issued to GB82WEST12345698765432` | `Refund issued to **********************` |
| `ip-address` | `Login from 192.0.2.10 and 2001:db8:0:0:0:0:0:1` | `Login from ********** and ********************` |
| `custom` | `User update {"user":"u-42","ssn":"123-45-6789","dateOfBirth":"1990-01-31"} password=hunter2` | `User update {"user":"u-42","ssn":"***********","dateOfBirth":"**********"} password=*******` |

### Try it locally

Masking is applied by the runtime's JSON log format, the one used on the Akka platform
and by the standalone image. The plain-text console format used by default for local
runs and tests is **not** sanitized, so select the JSON format explicitly:

```shell
SPIFFE_ENABLED=false mvn compile exec:java -Dlogback.configurationFile=logback.xml
curl localhost:9000/log-sanitization-demo/all
```

Each sample line shows up masked in the `message` field, for example:

```json
{"timestamp":"2026-10-29T02:01:36.288Z","logger":"com.example.api.LogSanitizationDemoEndpoint","message":"Password reset requested for ********************","severity":"INFO",...}
```

`GET /log-sanitization-demo/{kind}` accepts `email`, `phone`, `credit-card`, `iban`,
`ip-address`, `custom` or `all`, and returns a short confirmation that never contains
the sample values. Any other `kind` returns 400. The endpoint is open to all callers
because it is a demo.

The rules apply to every log line, including the runtime's own: the startup line, for
example, reads `Kalix Runtime started at *********:9000`.

### Known format gaps

The predefined groups don't fully mask every notation, which is why the samples above
use these formats. Observed with SDK 3.7.0:

| Input | Masked as |
|---|---|
| `+1 202 555 0143` (international, spaced) | `+1 ************` |
| `GB82 WEST 1234 5698 7654 32` (spaced IBAN) | not masked |
| `2001:db8::1` (compressed IPv6) | `**********1` |

Add a custom `pattern` or `class` entry if your logs carry these notations.

### Tests

- `SanitizationConfigScopeTest` checks that every entry applies to `logs` only and is not
  scoped to agents.
- `PredefinedLogSanitizersIntegrationTest` calls each of the six entries by name through
  `getSanitizerClient()` and asserts the exact masked output.
- `SensitiveFieldLogSanitizerTest` unit-tests the custom sanitizer (JSON, `key=value`,
  whitespace, case, partial names, escaped quotes, odd input).
- `LogSanitizationDemoEndpointIntegrationTest` covers the demo endpoint.

### Performance

Every entry that applies at `logs` runs on **every** log line of the service, including
Akka's own. Keep the number of rules and the complexity of regular expressions low, and
keep custom log sanitizers fast, free of logging, and free of exceptions.
`SensitiveFieldLogSanitizer` compiles its patterns once and returns a line without
sensitive fields untouched.

## CI/CD

A single GitHub Actions workflow, `.github/workflows/ci.yml`, handles both build
and release, following the pattern in
[ci-agents](https://github.com/sriramsundhar/ci-agents):

- **`build`** — runs on every push to any branch (this also covers pull requests
  from branches in this repo, since GitHub matches checks to a PR by commit SHA;
  there is no separate `pull_request` trigger, to avoid running the workflow
  twice for the same commit). Compiles, runs `mvn verify` (unit + integration
  tests), and builds the service image to confirm it is buildable.
- **`release`** — runs only on a push to `main` (`needs: build`). It does **not**
  deploy the service. Instead it:
  1. Checks `pom.xml`'s version matches semantic versioning,
     `MAJOR.MINOR.PATCH-SNAPSHOT` (e.g. `1.1.0-SNAPSHOT`); fails fast with a
     clear error otherwise.
  2. Strips `-SNAPSHOT` (e.g. `1.1.0-SNAPSHOT` -> `1.1.0`), commits that as
     `chore(release): 1.1.0 [skip ci]`, and tags it `v1.1.0`.
  3. Builds and tests the release-versioned jar and container image.
  4. Pushes the image to GHCR as `ghcr.io/<owner>/<repo>:1.1.0` and `:latest`.
     A self-hostable, Postgres-ready image (built with `-Pstandalone` on a Java 25 base)
     is also pushed as `:1.1.0-standalone`; the `build` job verifies it builds too.
  5. Bumps `pom.xml` to the next **patch** snapshot (e.g. `1.1.1-SNAPSHOT`),
     committed as
     `chore: prepare for next development iteration (1.1.1-SNAPSHOT) [skip ci]`.
  6. Pushes both commits and the tag back to `main`, then creates a GitHub
     Release for the tag with the built jar attached.

  Every merge to `main` is a **patch** release. To ship a minor or major
  release instead, edit `pom.xml`'s `<version>` to the desired
  `MAJOR.MINOR.0-SNAPSHOT` in the PR before merging — the release job picks
  that up and continues patch-bumping from there.

  The `[skip ci]` marker on both commits stops GitHub from re-triggering this
  workflow on its own pushes.

  If `main` has branch protection that requires pull request review or blocks
  direct pushes, step 5 will fail — allow the `github-actions[bot]` actor (or
  the workflow's token) to bypass that rule, or this step needs rethinking for
  your protection settings.

The workflow needs one repository secret:

- `AKKA_REPOSITORY_URL` — the private, token-scoped Maven repository URL that
  resolves the `io.akka:*` SDK artifacts (these are not on Maven Central). Find
  the value in your local `~/.m2/settings.xml` under the `akka-repository` /
  `akka-plugin-repository` entries — it was written there by `akka:setup` or
  `akka_sdd_init`. Treat it as a credential.

No `AKKA_TOKEN` or `AKKA_PROJECT_ID` is needed since the workflow does not talk
to the Akka platform. Pushing the version-bump commits and creating the GitHub
Release use the built-in `GITHUB_TOKEN`; the `release` job requests
`contents: write` and `packages: write` for that.

The `SERVICE_NAME` environment variable in the `release` job (default
`akka-sdd`) must match the `pom.xml` `artifactId` and the `docker.image`
property, since it is used to name the jar and the local image before it is
retagged for GHCR.

To actually deploy a built release to the Akka platform, follow
[CI/CD with GitHub Actions](https://doc.akka.io/operations/integrating-cicd/github-actions.html)
separately — it is intentionally not wired into this workflow.
