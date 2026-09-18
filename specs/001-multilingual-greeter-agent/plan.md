# Implementation Plan: Multilingual Greeter Agent

**Branch**: `001-multilingual-greeter-agent` | **Date**: 2026-09-17 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-multilingual-greeter-agent/spec.md`

**Note**: This template is filled in by the `/akka:plan` command. See `.akka/templates/plan-template.md` for the execution workflow.

## Summary

A greeter service where every user turn in a session gets back a short greeting phrase in a language not yet used earlier in that session, cycling back (least-recently-used first) once the ~50-language pool is exhausted. An HTTP endpoint accepts a turn, a Key Value Entity deterministically picks the next language and records it in the session's history (making rotation and concurrency-safety independently testable), and an Agent turns that language choice into an actual greeting phrase via an LLM call constrained to output only the greeting.

## Technical Context

**Language/Version**: Java 21+ (Akka SDK parent `io.akka:akka-javasdk-parent:3.6.3`)
**Primary Dependencies**: Akka Java SDK only — Agent, Key Value Entity, and HTTP Endpoint components; Anthropic model provider integration is bundled with the SDK (no extra Maven dependency)
**Storage**: Akka Key Value Entity durable state (runtime-managed); no external database
**Testing**: Akka SDK TestKit (JUnit 5) via `mvn test`; agent behavior tested deterministically with the TestKit's `TestModelProvider` (no real model calls in tests)
**Target Platform**: JVM microservice on the Akka runtime (local dev via `akka local start`; cloud via Akka platform deploy)
**Project Type**: Single Akka service (web-service) — Option 1 in Project Structure below
**Performance Goals**: No stringent target specified; reasonable default is a reply within a few seconds, bounded mainly by the configured model provider's latency (~P95 < 5s)
**Constraints**: Language pool is a fixed in-code catalog of ~50 languages (FR-002); single service, no multi-service split; agent calls require outbound network access to the configured model provider
**Scale/Scope**: One feature, 3 components (1 entity, 1 agent, 1 endpoint); sessions scale via Akka's automatic entity sharding — no fixed session-count ceiling (SC-003 requires sessions to run indefinitely)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Akka SDK First**: PASS. Everything is built on Akka SDK primitives (Key Value Entity, Agent, HTTP Endpoint); no third-party persistence, scheduling, or state-management library is introduced. The model provider integration is part of the SDK itself.
- **Design Principles**:
  - *Domain independence*: PASS. `Language` / `LanguagePool` and the rotation-selection algorithm are plain records/logic in the `domain` package with no Akka imports; `SessionLanguageEntity` (application layer) is a thin wrapper that calls into it.
  - *API isolation*: PASS. The HTTP endpoint defines its own request/response records (see `contracts/greeter-http-api.md`), not the entity's internal state shape.
  - *Right component for the job*: PASS — see Component Architecture table below, informed by `akka-context/sdk/components/index.html.md`.
  - *Single responsibility*: PASS. `SessionLanguageEntity` only tracks/selects rotation state; `GreeterAgent` only phrases a greeting in a given language; the endpoint only wires the two together and shapes HTTP I/O.
  - *Descriptive naming*: PASS. `SessionLanguageEntity`, `GreeterAgent`, `GreetingEndpoint`, `LanguagePool` — no generic `Manager`/`Service`/`Event` names.
- **Test Coverage**: PASS (planned). `/akka:tasks` will include unit tests for the rotation/exhaustion logic in the domain package, entity tests for `SessionLanguageEntity` (including concurrent-command ordering), and an agent test using `TestModelProvider` verifying the prompt constrains output to a greeting only.
- **Simplicity**: PASS. Considered and rejected a simpler "no LLM, static greeting map" design (see `research.md`) because the feature was explicitly requested as an agent and the SDK's own decision guide endorses an Agent for this shape of work; the chosen design is still minimal — one entity, one agent, one endpoint, no workflow, no view, no extra services.

No violations — Complexity Tracking table is empty (see below).

**Post-Phase-1 re-check**: The Component Architecture table and data model below match this evaluation exactly (same three components, same domain-class-only language pool); no new dependency, component, or pattern was introduced during design that wasn't already accounted for above. Re-affirmed: PASS, no violations.

## Project Structure

### Documentation (this feature)

```text
specs/001-multilingual-greeter-agent/
├── plan.md              # This file (/akka:plan command output)
├── research.md          # Phase 0 output (/akka:plan command)
├── data-model.md        # Phase 1 output (/akka:plan command)
├── quickstart.md        # Phase 1 output (/akka:plan command)
├── contracts/           # Phase 1 output (/akka:plan command)
│   └── greeter-http-api.md
└── tasks.md             # Phase 2 output (/akka:tasks command - NOT created by /akka:plan)
```

### Source Code (repository root)

```text
# Single Akka service (matches the scaffolded project layout)
src/
├── main/
│   ├── java/com/example/
│   │   ├── api/
│   │   │   └── GreetingEndpoint.java         # HTTP endpoint: POST /greetings/{sessionId}
│   │   ├── application/
│   │   │   ├── SessionLanguageEntity.java    # Key Value Entity: rotation state + selection
│   │   │   └── GreeterAgent.java             # Agent: phrases the greeting via the model
│   │   └── domain/
│   │       ├── Language.java                 # record(code, displayName)
│   │       └── LanguagePool.java             # fixed ~50-language catalog + rotation algorithm
│   └── resources/
│       └── application.conf                  # model-provider = anthropic, api-key = ${?ANTHROPIC_API_KEY}
└── test/
    └── java/com/example/
        ├── domain/LanguagePoolTest.java
        ├── application/SessionLanguageEntityTest.java
        └── application/GreeterAgentTest.java
```

**Structure Decision**: Single Akka service (Option 1) — this is one bounded-context feature (a greeter) with no need for a separately hosted frontend or additional services; it fits the project's existing `src/main/java/com/example/{api,application,domain}` layout from scaffolding.

## Component Architecture

*Filled during Phase 1 (design). Read the "Choosing a component type" section in `akka-context/sdk/components/index.html.md` before mapping — done above.*

| Domain concept / process | Component | Why this component | Rejected alternative and why not (close-call decisions) |
|--------------------------|-----------|--------------------|----------------------------------|
| Session's used-language rotation history | Key Value Entity (`SessionLanguageEntity`) | Only the current used-language list matters (no audit/history requirement beyond it); single-writer-per-instance processing gives race-free select-and-record for concurrent turns in the same session, satisfying FR-008 without extra locking | Event Sourced Entity: rejected — no business need for a record of "what happened, in what order, and why" beyond the current list itself, and no other component needs to react to individual "language used" events |
| Greeting phrasing for the selected language | Agent (`GreeterAgent`) | One LLM request-response per turn (no multi-step decision loop, no multi-agent coordination) that turns a chosen language into an actual greeting phrase, with the session id giving it per-conversation session memory | Autonomous Agent: rejected — no dynamic multi-step reasoning or agent-to-agent coordination is needed, the task is a single bounded request/response. Workflow wrapping a single agent call: rejected — the decision guide explicitly names this as the wrong pattern for one-shot LLM calls; there is no multi-step process to orchestrate or compensate |
| Turn intake / API surface | HTTP Endpoint (`GreetingEndpoint`) | Simple request/response demo API, easily exercised by curl/browser clients per quickstart.md | gRPC Endpoint: rejected — no non-browser service-to-service contract or schema-evolution need was identified for this feature; a plain JSON HTTP contract is sufficient and easiest to demo |
| Language pool + selection algorithm | Plain domain class (`Language`, `LanguagePool`) | Pure data/logic with no identity, state, subscription, schedule, or API surface of its own — the decision guide places "validation, calculation, transformation" logic in a plain domain class | — (not a close call) |

Rows for close-call decisions (entity type, view vs direct entity read, workflow vs consumer, timed action vs workflow timer) name the rejected alternative and why it was not chosen, per constitution "Right component for the job". No View is needed: the entity is always looked up by its own id (the session id), never by another attribute, so a direct `ComponentClient.forKeyValueEntity(sessionId)` read replaces what would otherwise require a View.

## Complexity Tracking

> No violations — this section intentionally left empty.
