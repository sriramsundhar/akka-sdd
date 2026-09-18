# Feature Specification: Multilingual Greeter Agent

**Feature Branch**: `001-multilingual-greeter-agent`
**Created**: 2026-09-17
**Status**: Draft
**Input**: User description: "greeter agent - greet each user in a new language every turn, remembering which languages were used per session"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Rotating greeting per turn (Priority: P1)

A user is chatting with the greeter agent within a session. Every time they send a message, the agent replies with a greeting in a language it has not already used earlier in that same session, so each turn feels fresh and multilingual.

**Why this priority**: This is the core value of the feature — without turn-by-turn language rotation, there is no product.

**Independent Test**: Can be fully tested by sending several consecutive messages within one session and confirming each reply's greeting language differs from all previous replies in that session.

**Acceptance Scenarios**:

1. **Given** a brand-new session with no prior turns, **When** the user sends their first message, **Then** the agent replies with only a greeting, in a language selected from the pool, and records that language as used for the session.
2. **Given** a session in which languages A and B have already been used, **When** the user sends another message, **Then** the reply's greeting is in a language other than A or B.
3. **Given** any turn, **When** the agent replies, **Then** the reply consists solely of the greeting (the agent does not answer or otherwise act on the content of the user's message).

---

### User Story 2 - Session-scoped memory (Priority: P2)

Different users, or the same user in different sessions, each get their own independent language rotation — one session's history of used languages never affects another session's choices.

**Why this priority**: Without per-session isolation, rotations would collide across unrelated conversations, breaking the "new language every turn" guarantee and leaking state between users.

**Independent Test**: Can be fully tested by running two sessions in parallel (or in sequence) and confirming the languages used in one session have no effect on which languages are offered in the other.

**Acceptance Scenarios**:

1. **Given** two distinct sessions that have each used a different set of languages, **When** a new turn occurs in session A, **Then** the language chosen is independent of session B's history and only excludes languages already used in session A.
2. **Given** a session that has had several turns and then goes idle, **When** the user sends another message in that same session later, **Then** the previously used languages for that session are still remembered and excluded from selection.

---

### User Story 3 - Pool exhaustion handling (Priority: P3)

Once a session has used every language in the pool at least once, the agent keeps working smoothly by restarting the rotation instead of failing or getting stuck.

**Why this priority**: Long-running sessions are a natural edge case; the feature must degrade gracefully rather than erroring once novelty runs out.

**Independent Test**: Can be fully tested by driving a single session through enough turns to exhaust the full language pool, then confirming subsequent turns still return valid greetings.

**Acceptance Scenarios**:

1. **Given** a session in which every language in the pool has already been used at least once, **When** the user sends another message, **Then** the agent replies with a valid greeting, choosing the language that was least recently used in that session.
2. **Given** the rotation has restarted at least once, **When** further turns occur, **Then** the agent continues to avoid repeating the immediately-preceding language(s) as long as a less-recently-used option exists.

### Edge Cases

- What happens on the very first turn of a session, when no languages have been used yet? The agent selects randomly from the full pool (see US1, Scenario 1).
- How does the system handle two turns arriving for the same session at effectively the same time? The session's used-language record MUST NOT lose or duplicate an update — each concurrent turn still receives a language distinct from every other language already recorded as used, including ones recorded by a turn processed just moments before.
- What happens if the user's message contains a request, question, or instruction? The agent still only replies with a greeting (see US1, Scenario 3) — it does not act on message content.
- What happens once the language pool is fully exhausted in a session? The rotation restarts, least-recently-used first (see US3).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST reply to every user turn with a greeting phrase in a single selected language, and with nothing else (no answer to the user's message content).
- **FR-002**: The system MUST draw the greeting's language from a fixed, curated pool of approximately 50 widely-spoken world languages.
- **FR-003**: For each turn after the first in a session, the system MUST select a language that has not yet been used earlier in that same session, as long as at least one such language remains in the pool.
- **FR-004**: The system MUST persist, for each session, the ordered history of languages already used in that session, so it can be consulted on every subsequent turn.
- **FR-005**: The system MUST scope this used-language history per session, such that no session's language selection is influenced by, or leaks into, another session's history.
- **FR-006**: When every language in the pool has been used at least once within a session, the system MUST restart the rotation by selecting the least-recently-used language for the pool, rather than erroring or stalling.
- **FR-007**: The system MUST identify, in a way recognizable to the user, which language each greeting is in (e.g., naming the language alongside the greeting).
- **FR-008**: The system MUST handle turns for the same session one at a time with respect to the used-language history, so that concurrent turns cannot both select the same "unused" language or corrupt the recorded history.

### Key Entities

- **Session**: Represents one ongoing conversation with the greeter agent. Identified by a session identifier supplied by the caller. Holds the ordered history of languages already used within it.
- **Language Pool**: The fixed, curated set of approximately 50 widely-spoken world languages the agent draws from when selecting a greeting language.
- **Turn**: A single user message and the corresponding greeting reply. Each turn selects exactly one language from the pool and appends it to its session's used-language history.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Across any single session, no two turns receive a greeting in the same language until every language in the pool has been used at least once in that session.
- **SC-002**: 100% of replies consist of a greeting only, with the language used identifiable by the recipient.
- **SC-003**: Sessions can run indefinitely (well beyond the ~50-language pool size) without ever failing to produce a valid greeting.
- **SC-004**: Running two or more sessions concurrently produces no observable cross-session influence on language selection — each session's rotation is independently verifiable from its own history alone.
