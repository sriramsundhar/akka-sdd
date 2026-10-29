# Implementation Plan: Log-Only Data Sanitization Sample

**Branch**: `002-sanitize-logging` | **Date**: 2026-10-28 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/002-sanitize-logging/spec.md`

## Summary

The service gets six log-only sanitizer entries: the five predefined groups plus one config-driven custom `LogSanitizer`. The custom one masks the values of named fields in JSON and `key=value` log text and keeps the names visible. An HTTP demo endpoint logs synthetic sample lines on demand. Tests cover config scope, every entry by name (via `SanitizerClient`), the custom sanitizer's edge cases, and the endpoint. The README gets observed before/after output.

This requires upgrading the SDK to 3.7.0 (research R1). Probing showed three things the plan accounts for:
- Masking only happens in the production JSON log layout, so `pom.xml` makes that layout the default for local runs and tests (R3, revised).
- Three of the requested sample strings aren't fully masked by the built-in groups, so fully-masked formats are used and the gaps are documented (R4).
- On 3.7.0, local runs can't use `-Dconfig.resource` (R2).

## Technical Context

**Language/Version**: Java 25 (`maven.compiler.release`)
**Primary Dependencies**: Akka SDK 3.7.0 (upgrade from 3.6.6); SLF4J and Typesafe Config come with the SDK. No new dependencies.
**Storage**: N/A (no state)
**Testing**: JUnit 5 + AssertJ; `TestKitSupport` (`getSanitizerClient()`, `httpClient`) for integration tests; surefire for `*Test`, failsafe for `*IntegrationTest`
**Target Platform**: Akka platform / standalone container (JSON logs, where masking applies); local `exec:java` for the demo
**Project Type**: single Akka web service
**Performance Goals**: The custom sanitizer runs on every log line. It uses two precompiled patterns, one `find()` each on the no-match path, and returns the same instance.
**Constraints**: Every entry `apply-at = ["logs"]`, with no `agents`/`agent-roles`; equal-length `*` masks; synthetic data only; the sanitizer never logs and has no throwing code paths
**Scale/Scope**: 6 config entries, 1 sanitizer class, 1 endpoint, 4 test classes, 1 README section, plus the upgrade (pom + README run commands)

## Constitution Check

*Pre-Phase 0 and post-Phase 1 (re-evaluated after design; unchanged).*

| Principle | Status | Notes |
|---|---|---|
| I. Akka SDK First | ✅ | Uses the SDK's runtime sanitization; no third-party libraries. SDK version bump is the only dependency change, and it's justified (R1). |
| II. Domain independence | ✅ | No business domain logic. The masking logic is a runtime plugin, not domain logic. |
| II. API isolation | ✅ | Endpoint returns its own plain-text responses; no domain types. |
| II. Right component | ✅ | See Component Architecture; close calls name the rejected alternative. |
| II. Single responsibility / naming | ✅ | One purpose per class; descriptive names given by the request. |
| II. Package layering | ⚠️ justified | `com.example.sanitizer` sits outside `api/application/domain`. See Complexity Tracking. |
| III. Test Coverage | ✅ | Unit tests for the sanitizer and the config scope; integration tests for the entries and the endpoint. Test names describe behavior. |
| IV. Simplicity | ✅ | No domain extraction, no custom logback file, no extra abstractions. |

## Project Structure

### Documentation (this feature)

```text
specs/002-sanitize-logging/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/log-sanitization-demo.md
├── checklists/requirements.md
└── tasks.md             # /akka:tasks
```

### Source Code (repository root)

```text
pom.xml                                            # parent 3.6.6 -> 3.7.0
README.md                                          # local-run command for 3.7.0 + "Log sanitization sample" section
src/main/resources/application.conf                # + akka.javasdk.sanitization.sanitizers (6 entries)
src/main/java/com/example/
├── sanitizer/SensitiveFieldLogSanitizer.java      # implements LogSanitizer
└── api/LogSanitizationDemoEndpoint.java           # GET /log-sanitization-demo/{kind}
src/test/java/com/example/
├── sanitizer/SensitiveFieldLogSanitizerTest.java  # unit
├── sanitizer/SanitizationConfigScopeTest.java     # unit: log-only scope of every entry
├── sanitizer/PredefinedLogSanitizersIntegrationTest.java  # getSanitizerClient(), 5 predefined + custom by name
└── api/LogSanitizationDemoEndpointIntegrationTest.java    # httpClient: 7 kinds -> 200 w/o raw values, unknown -> 400
```

**Structure Decision**: Single Akka service using the existing `com.example` packages, plus the `sanitizer` package the request specifies.

## Component Architecture

Read: "Choosing a component type" (`akka-context/sdk/components/index.html.md`).

| Domain concept / process | Component | Why this component | Rejected alternative and why not (close-call decisions) |
|---|---|---|---|
| Log masking policy (5 predefined groups) | Configuration only (`akka.javasdk.sanitization.sanitizers`) | The runtime applies it to every log line; no code needed | Ad hoc `Sanitizer`/`SanitizerClient` calls before logging: per-call-site, easy to forget, and this would be the `client` point, which is out of scope |
| Sensitive-field masking | Plain class implementing `akka.javasdk.LogSanitizer` (runtime plugin, not a component) | The runtime constructs it from config with a `SanitizerContext` and calls it for every log line | `pattern` regex entry: masks the whole match including the field name, and the field list can't come from config. Plain domain class + wrapper: an extra class with no benefit (YAGNI) |
| On-demand demo trigger | HTTP Endpoint | Request-scoped, stateless, callable with curl | Timed Action / `ServiceSetup.onStartup` logging: not on demand, and can't select a kind or return 400 |
| Sample lines | `private static final` constants in the endpoint | Fixed data used only by the endpoint; tests keep their own copies so a changed constant can't silently pass | Entity/config-driven samples: no state or variability needed |

No entities, views, workflows, consumers or agents.

## Design Notes

- **Endpoint**: `HttpResponse get(String kind)` uses a `switch` over the kinds. It logs through a `private static final Logger` (SLF4J) at INFO and returns `HttpResponses.ok("Logged N sample line(s) for '<kind>'. Check the service logs.")` or `HttpResponses.badRequest(...)` (see contract). Plain-text responses only.
- **SensitiveFieldLogSanitizer**: see research R6 for the exact patterns, the same-instance fast path, the empty field list, and null handling. It has one public constructor `(SanitizerContext)`.
- **Upgrade changes outside the sample**:
  - `pom.xml` parent version → 3.7.0.
  - README local-dev command becomes `AGENT_MODEL_PROVIDER=litellm mvn compile exec:java`, since `-Dconfig.resource=application-dev.conf` no longer starts on 3.7.0 (R2).
  - The existing greeter tests must pass unchanged.
- **README section**: what's configured; the before/after table re-captured from a real run of the final code (JSON log format, the pom default); the run command; a note that the dev-mode console isn't masked; the known format gaps (R4); the runtime's own lines being masked (e.g. the bind address); and the performance note (every `logs` entry runs on every log line — keep rule count and regex complexity low).

## Exit-condition checks (advisory, À la carte)

The five checks captured during `/akka:specify` map onto these test classes: `SanitizationConfigScopeTest`, `PredefinedLogSanitizersIntegrationTest`, `SensitiveFieldLogSanitizerTest`, `LogSanitizationDemoEndpointIntegrationTest`, and `mvn verify`. Tests must assert exact strings, not just "contains `*`".

## Risks

- **Standalone container on 3.7.0**: verified after implementation (R8). It starts, uses Postgres from `DB_*`, and masks the demo lines. Its startup config dump exposed the license and model API keys, so those paths are now in `akka.runtime.sensitive-config-paths`.
- `application-dev.conf` could not be loaded via `-Dconfig.resource` on 3.7.0, and the standalone image never loaded it, so it was removed (R8). The local-dev command uses env vars.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `com.example.sanitizer` package outside `api/application/domain` | The request names this class and package; the class is a runtime logging plugin, not domain, application or API code | Moving it to `domain` would mislabel infrastructure as business logic; `application` holds Akka components, and this isn't one |
| SDK upgrade inside a sample feature | The required APIs don't exist in 3.6.6 | No alternative delivers log-only scope or a custom `LogSanitizer` |
