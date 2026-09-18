# Research: Multilingual Greeter Agent

## Decision: Model provider for the greeting agent

**Decision**: Default to the **Anthropic** model provider, configured in `application.conf` via `akka.javasdk.agent.model-provider = anthropic`, with the API key sourced from the `ANTHROPIC_API_KEY` environment variable. The provider stays swappable through configuration alone (no code change) since Akka's agent model selection is externalized to config.

**Rationale**: No AI provider key was configured in this environment (checked during `/akka:setup`, deferred). The Akka SDK ships built-in integration for several hosted providers (Anthropic, OpenAI, Azure OpenAI, Bedrock, Google AI Gemini, Hugging Face, Vertex AI) with no extra Maven dependency required — selection is purely `application.conf`. Anthropic is a reasonable, commonly available default for this environment; the actual key must be supplied by whoever runs the service locally or in the cloud (documented in quickstart.md), which is an environment/deployment concern, not a spec blocker.

**Alternatives considered**:
- **OpenAI** — equally supported, same config shape (`model-provider = openai`). Not chosen only because no key preference was indicated; switching later is a one-line config change.
- **Hardcode a specific paid model without a config option** — rejected: the constitution's Simplicity principle and the SDK's own `ModelProvider.fromConfig` pattern favor keeping this externally configurable rather than baked into code.
- **Skip the LLM entirely, return a canned greeting from a static per-language map** — rejected: the feature was explicitly requested as a "greeter **agent**," and Akka's decision guide endorses an Agent for exactly this shape of work ("one LLM request-response, with tools and session memory"). A static lookup would trivially satisfy the rotation/session requirements but would not be an agent at all, missing the point of the request. The LLM call is scoped tightly (see `FUNC-GREETER-REPLY-GREETING-ONLY`) via a constrained system prompt so it stays a one-line greeting rather than open-ended conversation.

## Decision: Where "used-language" rotation state lives

**Decision**: A dedicated `SessionLanguageEntity` (Key Value Entity), keyed by the same session id used for the agent call, holds the ordered history of languages used in that session. The entity's command handler computes the next language deterministically (unused-first, then least-recently-used) and returns it; the endpoint then passes that language into the agent call as an instruction.

**Rationale**: Determinism and testability. Asking the LLM itself to "remember" which languages were used and to enforce the no-repeat/LRU-restart rule would make the core invariants (`FUNC-GREETER-ROTATION-NO-REPEAT`, `FUNC-GREETER-POOL-EXHAUSTION-RECOVERY`) unverifiable by a deterministic test — model output is not guaranteed reproducible. A Key Value Entity's single-writer-per-instance guarantee (Akka processes one command at a time per entity instance) also directly satisfies the concurrency-safety requirement (`FUNC-GREETER-CONCURRENT-TURN-SAFETY`, `FR-008`) without any additional locking code.

**Alternatives considered**:
- **Store the used-language list in the Agent's own session memory** — rejected: session memory holds conversational turns (message/response history) for LLM context, not a structured, programmatically-queryable invariant the code must enforce exactly. Mixing the two would make the rotation rule dependent on parsing prior conversation text.
- **Event Sourced Entity instead of Key Value Entity** — rejected: no audit trail or "what happened, when, and why" business requirement exists for this feature (see Component Architecture table in plan.md); only the current used-language history matters, and no other component needs to react to individual "language used" events.

## Decision: Language pool representation

**Decision**: A fixed, in-code list of ~50 `Language(code, displayName)` records in the `domain` package (no external dependency, no database table), matching FR-002.

**Rationale**: The pool is static and small; a plain domain constant satisfies "Right component for the job" (pure data with no state, subscription, schedule, or API surface does not need a component) and the Simplicity principle (no database or config-driven catalog needed for ~50 fixed entries).

**Alternatives considered**:
- **Configurable pool via `application.conf`** — rejected as unnecessary for now (YAGNI); nothing in the spec calls for the pool to change without a code change.
