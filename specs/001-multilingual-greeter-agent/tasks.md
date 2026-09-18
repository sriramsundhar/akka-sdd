---
description: "Task list for feature implementation"
---

# Tasks: Multilingual Greeter Agent

**Input**: Design documents from `/specs/001-multilingual-greeter-agent/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/greeter-http-api.md, quickstart.md

**Tests**: Included. The constitution's Test Coverage principle requires unit/integration tests for every behavioral change, and five of these tests are the delegated checks behind the exit conditions approved during `/akka:specify` (`GreeterRotationTest`, `GreeterSessionIsolationTest`, `GreeterPoolExhaustionTest`, `GreeterReplyScopeTest`, `GreeterConcurrencyTest`).

**Organization**: Tasks are grouped by user story (spec.md priorities P1/P2/P3) to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Paths are exact, under the existing scaffolded layout `src/main/java/com/example/{api,application,domain}` and `src/test/java/com/example/{application,domain}`

## Phase 1: Setup

**Purpose**: Confirm the scaffolded project is ready for feature work (no new project scaffolding needed — `/akka:setup` already created `pom.xml` and the package layout).

- [X] T001 Verify baseline build with `mvn compile` against the existing `pom.xml` (Akka SDK parent `3.6.3`, no new dependencies needed per research.md)
- [X] T002 [P] Confirm/create test package directories `src/test/java/com/example/domain/` and `src/test/java/com/example/application/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared domain data and configuration every user story depends on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T003 [P] Create `Language` record (`code`, `displayName`) in src/main/java/com/example/domain/Language.java
- [X] T004 Create `LanguagePool` catalog (fixed list of ~50 `Language` entries, `LanguagePool.ALL`) in src/main/java/com/example/domain/LanguagePool.java (depends on T003)
- [X] T005 [P] Configure `application.conf` model provider: `akka.javasdk.agent.model-provider = anthropic`, `anthropic.model-name`, `anthropic.api-key = ${?ANTHROPIC_API_KEY}` in src/main/resources/application.conf (per research.md)

**Checkpoint**: Foundation ready — user story implementation can now begin

---

## Phase 3: User Story 1 - Rotating greeting per turn (Priority: P1) 🎯 MVP

**Goal**: Every user turn gets a reply consisting solely of a greeting phrase in a language not yet used earlier in that session; the first turn picks randomly from the pool.

**Independent Test**: Send several consecutive `POST /greetings/{sessionId}` requests with the same `sessionId` and confirm each response's `language` differs from every previous response in that session, and `greeting` contains nothing beyond the greeting phrase.

### Tests for User Story 1 ⚠️

> Write these tests FIRST, ensure they FAIL before implementation

- [X] T006 [P] [US1] Unit test for `LanguagePool.selectNext` (first-turn random pick from the full pool; subsequent-turn excludes already-used codes) in src/test/java/com/example/domain/LanguagePoolTest.java
- [X] T007 [P] [US1] Integration test asserting no language repeats across consecutive turns of one session, via `SessionLanguageEntity` in src/test/java/com/example/application/GreeterRotationTest.java
- [X] T008 [P] [US1] Agent test (using the TestKit's `TestModelProvider`) asserting `GreeterAgent`'s reply contains only the greeting for the requested language, nothing else, in src/test/java/com/example/application/GreeterReplyScopeTest.java
- [X] T009 [P] [US1] Integration test asserting concurrent turns for the same session never select a duplicate "unused" language nor corrupt the recorded history, in src/test/java/com/example/application/GreeterConcurrencyTest.java

### Implementation for User Story 1

- [X] T010 [P] [US1] Implement `LanguagePool.selectNext(List<String> usedCodesOldestFirst, Random random)` covering all three rotation cases (first-turn random / unused-pick / exhausted-pool least-recently-used) in src/main/java/com/example/domain/LanguagePool.java (depends on T004; makes T006 pass)
- [X] T011 [US1] Implement `SessionLanguageEntity` (Key Value Entity, state per data-model.md `SessionLanguageHistory`) with a `selectNextLanguage` command handler delegating to `LanguagePool.selectNext` and updating `usedLanguageCodes` in src/main/java/com/example/application/SessionLanguageEntity.java (depends on T010; makes T007 and T009 pass)
- [X] T012 [P] [US1] Implement `GreeterAgent` with a `greet(String languageDisplayName)` command handler whose system message constrains the model to output only a short greeting phrase in that language in src/main/java/com/example/application/GreeterAgent.java (depends on T005; makes T008 pass)
- [X] T013 [US1] Implement `GreetingEndpoint` (`@HttpEndpoint("/greetings")`, public ACL) with `POST /greetings/{sessionId}` calling `SessionLanguageEntity::selectNextLanguage` then `GreeterAgent::greet` per contracts/greeter-http-api.md, in src/main/java/com/example/api/GreetingEndpoint.java (depends on T011, T012)
- [X] T014 [US1] Add request validation (`400` on blank/missing `message`) and model-provider failure mapping (`502`) in src/main/java/com/example/api/GreetingEndpoint.java (depends on T013)

**Checkpoint**: User Story 1 is fully functional and independently testable (MVP).

---

## Phase 4: User Story 2 - Session-scoped memory (Priority: P2)

**Goal**: Different sessions never influence each other's language rotation, and a session's history survives across turns separated in time.

**Independent Test**: Drive two distinct `sessionId`s through several turns each (interleaved or sequential) and confirm the language sequence seen in one session is unaffected by the other's turns; confirm a session's history is still honored on a later turn after a gap.

### Tests for User Story 2 ⚠️

- [X] T015 [P] [US2] Integration test driving two sessions and asserting independent, non-interfering language rotation (including a delayed turn still excluding that session's own prior languages) in src/test/java/com/example/application/GreeterSessionIsolationTest.java

### Implementation for User Story 2

- [X] T016 [US2] Review `SessionLanguageEntity` and `GreetingEndpoint` to confirm both the entity id and the agent's `.inSession(...)` id are derived solely from the request's `sessionId` path segment, with no shared or static mutable state between instances; fix if any cross-session state is found, in src/main/java/com/example/application/SessionLanguageEntity.java and src/main/java/com/example/api/GreetingEndpoint.java (depends on T013; makes T015 pass)

**Checkpoint**: User Stories 1 and 2 both independently functional.

---

## Phase 5: User Story 3 - Pool exhaustion handling (Priority: P3)

**Goal**: Once a session has used every language in the pool at least once, further turns keep returning valid greetings by restarting the rotation least-recently-used-first, instead of failing.

**Independent Test**: Drive a single session through enough turns to exhaust the full ~50-language pool, then confirm subsequent turns still return `200 OK` with valid greetings, selecting the least-recently-used language first.

### Tests for User Story 3 ⚠️

- [X] T017 [P] [US3] Integration test driving one session through full pool exhaustion and asserting least-recently-used restart behavior with no error, in src/test/java/com/example/application/GreeterPoolExhaustionTest.java

### Implementation for User Story 3

- [X] T018 [US3] Verify the exhausted-pool branch of `LanguagePool.selectNext` (built in T010) satisfies T017; correct it if the least-recently-used ordering or list re-append logic has a gap, in src/main/java/com/example/domain/LanguagePool.java (depends on T010, T017)

**Checkpoint**: All three user stories independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T019 [P] Add structured logging (session id, selected language, turn outcome) in src/main/java/com/example/api/GreetingEndpoint.java and src/main/java/com/example/application/SessionLanguageEntity.java
- [X] T020 [P] Walk through quickstart.md end-to-end (`mvn test`, `akka local start`, curl examples) and correct any drift in specs/001-multilingual-greeter-agent/quickstart.md
- [X] T021 Run the full suite with `mvn test` and confirm all exit-condition-backed tests pass: src/test/java/com/example/application/GreeterRotationTest.java, GreeterSessionIsolationTest.java, GreeterPoolExhaustionTest.java, GreeterReplyScopeTest.java, GreeterConcurrencyTest.java

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **User Stories (Phase 3-5)**: All depend on Foundational; proceed in priority order (P1 → P2 → P3) or in parallel if staffed, per notes below
- **Polish (Phase 6)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Starts after Foundational. No dependency on other stories.
- **User Story 2 (P2)**: Starts after Foundational. Reviews/adjusts US1's files (T013) but adds no new production component — independently testable via T015 once T013 exists.
- **User Story 3 (P3)**: Starts after Foundational. Verifies/extends the shared rotation algorithm from US1 (T010) — independently testable via T017 once T010 exists.

### Within Each User Story

- Tests are written first and must fail before their corresponding implementation task
- Domain logic (LanguagePool) before the entity that uses it
- Entity and agent before the endpoint that wires them together
- Component types (Key Value Entity, Agent, HTTP Endpoint) come from the Component Architecture table in plan.md — not changed here

### Parallel Opportunities

- T002 can run parallel to T001
- T003 and T005 can run in parallel (different files); T004 depends on T003
- T006, T007, T008, T009 (all US1 tests) can run in parallel
- T010 and T012 can run in parallel (different files, independent of each other); T011 depends on T010, T013 depends on T011 and T012
- T019 and T020 can run in parallel

---

## Parallel Example: User Story 1

```bash
# Tests together:
Task: "Unit test for LanguagePool.selectNext in src/test/java/com/example/domain/LanguagePoolTest.java"
Task: "Integration test for no-repeat rotation in src/test/java/com/example/application/GreeterRotationTest.java"
Task: "Agent reply-scope test in src/test/java/com/example/application/GreeterReplyScopeTest.java"
Task: "Concurrency-safety test in src/test/java/com/example/application/GreeterConcurrencyTest.java"

# Independent implementation tracks together:
Task: "Implement LanguagePool.selectNext in src/main/java/com/example/domain/LanguagePool.java"
Task: "Implement GreeterAgent in src/main/java/com/example/application/GreeterAgent.java"
```

## Parallel Example: User Story 2 and User Story 3

Once Foundational and US1's T010/T013 exist, US2's T015-T016 and US3's T017-T018 touch disjoint test files and can proceed in parallel by different developers.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: run T006-T009, confirm they pass; exercise quickstart.md's curl examples
5. Demo the MVP: rotating greetings within a single session

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. User Story 1 → validate independently → MVP demo
3. User Story 2 → validate independently (two-session isolation) → demo
4. User Story 3 → validate independently (pool exhaustion) → demo
5. Polish → logging, quickstart validation, full suite confirmation

---

## Notes

- [P] tasks touch different files with no unmet dependency
- Each user story's checkpoint is independently demoable without later stories
- Tests T006-T009, T015, T017 map directly to the five exit conditions approved in `/akka:specify` — do not rename their classes
- Commit after each task or logical group
