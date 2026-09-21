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
- **`release`** — runs only on a push to `main` (`needs: build`). Rebuilds the
  container image, installs the [Akka CLI](https://github.com/akka/setup-akka-cli-action),
  and deploys the image with `akka service deploy`.

Both jobs need repository secrets:

- `AKKA_REPOSITORY_URL` — the private, token-scoped Maven repository URL that
  resolves the `io.akka:*` SDK artifacts (these are not on Maven Central). Find
  the value in your local `~/.m2/settings.xml` under the `akka-repository` /
  `akka-plugin-repository` entries — it was written there by `akka:setup` or
  `akka_sdd_init`. Treat it as a credential.
- `AKKA_TOKEN` — a service token created with `akka project token create --description "GitHub Actions"`
  (`release` job only)
- `AKKA_PROJECT_ID` — the target project's UUID, from `akka projects list`
  (`release` job only)

See [CI/CD with GitHub Actions](https://doc.akka.io/operations/integrating-cicd/github-actions.html)
for background on the latter two.

The `AKKA_SERVICE_NAME` environment variable in the `release` job (default
`akka-sdd`) must match the service name used with `akka service deploy`.
