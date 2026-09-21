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
  1. Strips `-SNAPSHOT` from the `pom.xml` version (e.g. `1.0-SNAPSHOT` -> `1.0`),
     commits that as `chore(release): 1.0 [skip ci]`, and tags it `v1.0`.
  2. Builds and tests the release-versioned jar and container image.
  3. Pushes the image to GHCR as `ghcr.io/<owner>/<repo>:1.0` and `:latest`.
  4. Bumps `pom.xml` to the next snapshot (e.g. `1.1-SNAPSHOT`), committed as
     `chore: prepare for next development iteration (1.1-SNAPSHOT) [skip ci]`.
  5. Pushes both commits and the tag back to `main`, then creates a GitHub
     Release for the tag with the built jar attached.

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
