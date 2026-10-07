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
mvn compile exec:java
```

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
`application.conf` under `akka.persistence.r2dbc` and read from `DB_HOST`, `DB_PORT`,
`DB_DATABASE`, `DB_USER` and `DB_PASSWORD` (defaults: `localhost:5432`, database, user and
password all `postgres`; set `DB_PASSWORD` for anything other than local development). Tables are created automatically.

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
