# Research: Log-Only Data Sanitization Sample

All findings below were verified on 2026-10-28 against a scratch copy of this project built on SDK 3.7.0 (runtime 1.7.2): probes ran through `getSanitizerClient()` in a TestKit integration test and through a real `mvn compile exec:java` run. The repository itself was not modified during research.

## R1 — SDK version

- **Decision**: Upgrade `akka-javasdk-parent` 3.6.6 → 3.7.0.
- **Rationale**: 3.6.6 has only `akka.javasdk.Sanitizer` and the older `predefined-sanitizers` / `regex-sanitizers` config. Named `sanitizers { … apply-at … }` entries, `LogSanitizer`, `SanitizerContext`, `SanitizerClient` and `TestKitSupport.getSanitizerClient()` first appear in 3.7.0. On 3.7.0, main and test sources compile unchanged, and a TestKit integration test runs.
- **Alternatives considered**: Stay on 3.6.6 with `regex-sanitizers`. Rejected: no per-entry `apply-at` (so it cannot be logs-only), and no custom log sanitizer class.

## R2 — Upgrade side effects on local runs (must be handled in this feature)

| Observed on 3.7.0 | Cause | Decision |
|---|---|---|
| Startup fails: *"SPIFFE is enabled but no trust domain is configured"*, seen **only** together with a `-Dconfig.resource` override | Runtime 1.7.2 defaults `akka.runtime.spiffe.enabled = true` (env `SPIFFE_ENABLED`); the dev-mode defaults that a plain run gets are bypassed by the override | No action: plain `mvn compile exec:java` starts without `SPIFFE_ENABLED` (re-verified 2026-10-28 after a user report; the earlier "needed for local runs" conclusion was wrong). |
| Startup fails with any `-Dconfig.resource=…` (even a two-line file that only includes `application.conf`): *"R2DBC database config scheme has changed…"* | The override bypasses the runtime's dev-mode persistence defaults on 3.7.0 | The README's local-dev command switches from `-Dconfig.resource=application-dev.conf` to the existing env override `AGENT_MODEL_PROVIDER=litellm` (already read by `application.conf`). `application-dev.conf` is left as is; its container note stays. |
| Plain `mvn compile exec:java` | — | Starts fine. |

- **Standalone container image**: verified later, see R8.

## R3 — Where log masking actually happens

- **Finding**: Only `kalix.runtime.LogbackJsonLayout` (the production `logback.xml` in the runtime jar) calls the sanitizer engine. The dev-mode console layout (`logback-runtime-dev-mode.xml`, which the parent pom sets for `exec:java` and tests) does **not**. Verified: with the six entries configured, the dev console printed every value unmasked. With `-Dlogback.configurationFile=logback.xml`, the same lines came out fully masked as JSON, including the custom class and SLF4J `{}` arguments.
- **Decision (revised after user feedback)**: `pom.xml` sets the `logback.configurationFile` property to `logback.xml`, so plain `mvn compile exec:java` and tests log as JSON with masking applied. The readable console format remains available with `-Dlogback.configurationFile=logback-runtime-dev-mode.xml`. Originally the flag was opt-in, but a user running the plain command saw unmasked logs and chose JSON-by-default.
- **Effect on tests**: test logs are JSON and masked too (verified: the endpoint test's sample lines appear masked in `mvn verify` output). Tests still verify masking through the sanitizer client, which doesn't depend on the log format.
- **Alternatives considered**: (a) Add a project `logback` file that wraps the JSON layout in a pattern layout. Rejected: more config to own, and it duplicates the runtime file. (b) Assert masking from captured log output in tests. Rejected: the TestKit logs through the dev layout, so masking can't be observed there; the sanitizer client is the supported test path.

## R4 — Predefined group behavior and sample formats

Observed output of `getSanitizerClient().sanitize(name, text)`:

| Entry | Input | Output | Verdict |
|---|---|---|---|
| log-email | `Password reset requested for jane.doe@example.com` | `Password reset requested for ********************` | full (20) |
| log-phone | `SMS verification sent to +1 202 555 0143` | `SMS verification sent to +1 ************` | **partial**, `+1 ` left |
| log-phone | `… +12025550143` / `… +1-202-555-0143` / `… (202) 555-0143` | `+***********` / `+1-************` / `(*************` | partial |
| log-phone | `… +44 20 7946 0958` | unchanged | not matched |
| log-phone | `SMS verification sent to 202-555-0143` | `SMS verification sent to ************` | **full (12)** ✔ |
| log-credit-card | `Payment declined for card 4111 1111 1111 1111` | `Payment declined for card *******************` | full (19) ✔ |
| log-iban | `Refund issued to GB82 WEST 1234 5698 7654 32` | unchanged | **not matched** |
| log-iban | `Refund issued to GB82WEST12345698765432` | `Refund issued to **********************` | **full (22)** ✔ |
| log-ip-address | `Login from 192.0.2.10 and 2001:db8::1` | `Login from ********** and **********1` | **partial IPv6** |
| log-ip-address | `Login from 192.0.2.10 and 2001:db8:0:0:0:0:0:1` | `Login from ********** and ********************` | **full (10, 20)** ✔ |

Cross-checks: the phone rule doesn't touch the card, IBAN, IP, SSN/date or timestamp samples. The card rule masks the digit tail of a *spaced* IBAN but not the compact one. With all entries active together in a real run, the chosen samples mask exactly as above. The IP rule also masks the runtime's own `127.0.0.1` in `Akka Runtime started at *********:9077`.

- **Decision**: Use the ✔ formats as sample lines (all synthetic: 555-01xx fictional range, the documentation IBAN example, Visa test card, RFC 5737 / RFC 3849 documentation IPs). List the partial formats in the README as known gaps.
- **Rationale**: The requested outcome ("fully masked") can't be met with the requested strings. Changing the sample, not the masking, honours "don't change the predefined behavior".
- **Alternatives considered**: Keep the requested strings and document partial masking. Rejected: the demo would show raw PII digits, which contradicts the sample's purpose.

## R5 — Does `SanitizerClient` reach a class-based, logs-only entry?

- **Finding**: Yes. A `class = …, apply-at = ["logs"]` entry is masked by `getSanitizerClient().sanitize("<name>", text)` (javadoc: "Reaches every configured sanitizer").
- **Decision**: The integration test also covers `log-sensitive-fields` by name, on top of the unit tests. The README fallback note is not needed.

## R6 — Custom sanitizer design

- **Decision**: One class, `com.example.sanitizer.SensitiveFieldLogSanitizer implements LogSanitizer`, with a single public constructor `(SanitizerContext)`. It reads `context.config().getStringList("fields")` and compiles two case-insensitive patterns once:
  - JSON: `"(<names>)"(\s*:\s*)"((?:[^"\\]|\\.)*)"`, which masks group 3 (an escaped quote stays inside the value).
  - key=value: `(?<![A-Za-z0-9_])(<names>)=([^\s&,;]+)`, which masks group 2.
  - `<names>` = `Pattern.quote` of each name joined by `|`. Whole-name matching comes from the surrounding quotes (JSON) and the look-behind plus the literal `=` (kv).
- **Unchanged on no match**: `sanitize` returns the *same instance* when neither pattern finds a match; an empty field list means no patterns at all. A null message is returned as-is.
- **No throwing / no logging**: there are no I/O or parse paths, the patterns are prebuilt, and there is no try/catch. If a bug ever did throw, the runtime replaces the log line with the error reason (fail-closed, per the `LogSanitizer` javadoc) rather than leaking it. Missing `fields` config fails at startup (config error), never per line.
- **Testability**: `SanitizerContext` is a plain interface, so unit tests pass a lambda/anonymous context backed by `ConfigFactory.parseString`. No masking logic is split into `domain`: it isn't business logic, and splitting adds a class with no benefit (constitution IV).
- **Alternatives considered**: A single `regex-sanitizer` / `pattern` entry. Rejected: it masks the whole match including the field name, and the field list isn't config-driven. Also rejected: implementing `TextSanitizer` as well, because the scope is logs only.

## R7 — Log-only scope check

- **Decision**: Add a unit test, `SanitizationConfigScopeTest`, that parses `application.conf` from the classpath (`ConfigFactory.parseResources`, unresolved; there is no `src/test/resources` override). It asserts that the six entries exist, and that **every** entry under `akka.javasdk.sanitization.sanitizers` has `apply-at == ["logs"]` and no `agents` / `agent-roles`.

## R8 — Standalone container on 3.7.0 (verified 2026-10-29)

- **Finding**: The `-Pstandalone` image (Java 25 base) starts against Postgres with the env vars `compose.yml` sets. Its entrypoint passes `-Dconfig.resource=runtime-standalone.conf`. That runtime file disables SPIFFE, reads `DB_HOST`/`DB_PORT`/`DB_DATABASE`/`DB_USER`/`DB_PASSWORD`, and then includes `application.conf`. So `DB_*` takes effect without any project config, and the README's earlier claim that it had no effect was wrong. All six demo lines came out masked, and an unknown kind returned 400.
- **Finding**: At startup, `AkkaRuntimeMain` logs the whole config at INFO. It leaves out only the paths in `akka.runtime.sensitive-config-paths`. The default list doesn't cover `akka.license-key` or `akka.javasdk.agent.*.api-key`, so the license key and the Vertex AI key were logged in clear text. The log sanitizers don't mask them.
- **Decision**: `application.conf` appends `akka.license-key` and `akka.javasdk.agent` to `akka.runtime.sensitive-config-paths`. Re-verified: neither key appears in the logs, the license is still accepted, and the default exclusions (for example the r2dbc `connection-factory`) still apply. `application-dev.conf` is removed. It couldn't be loaded locally on 3.7.0, the image never loaded it, and its r2dbc block duplicated `runtime-standalone.conf`.
