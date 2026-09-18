# Data Model: Multilingual Greeter Agent

## Language (domain value, not persisted)

Plain domain record in `com.example.domain`. Defined once as a fixed, ordered list of ~50 entries (`LanguagePool`).

| Field | Type | Notes |
|---|---|---|
| `code` | `String` | Stable identifier for the language (e.g. ISO 639-1 code such as `"fr"`, `"ja"`). Used as the key for rotation bookkeeping. |
| `displayName` | `String` | Human-readable English name shown/spoken to the user alongside the greeting (satisfies FR-007), e.g. `"French"`, `"Japanese"`. |

No relationships; immutable constant catalog (`LanguagePool.ALL`, order fixed at startup).

## SessionLanguageHistory (Key Value Entity state)

Owned by `SessionLanguageEntity`, one instance per session id (`com.example.application`).

| Field | Type | Notes |
|---|---|---|
| `usedLanguageCodes` | `List<String>` | Language `code`s already used in this session, ordered **oldest-used first, most-recently-used last**. Empty for a brand-new session. |

**Validation rules**:
- Every code in `usedLanguageCodes` MUST correspond to an entry in `LanguagePool.ALL`.
- No code appears more than once in the list at any time — a reused code (after pool exhaustion) is removed from its old position and re-appended at the end, not duplicated. This is what makes "least-recently-used" a simple "take the front of the list" operation.

**State transitions** (all via the single `selectNextLanguage` command handler, so per-instance sequential processing makes each transition atomic w.r.t. concurrent turns for the same session — satisfies FR-008):

1. **First turn** (`usedLanguageCodes` empty): pick uniformly at random from `LanguagePool.ALL`; append its code.
2. **Subsequent turn, pool not exhausted** (`usedLanguageCodes.size() < LanguagePool.ALL.size()`): pick uniformly at random from the languages in `LanguagePool.ALL` whose code is **not** in `usedLanguageCodes`; append its code.
3. **Subsequent turn, pool exhausted** (`usedLanguageCodes.size() == LanguagePool.ALL.size()`): pick the code at the **front** of `usedLanguageCodes` (least-recently-used); remove it from the front and re-append it at the end.

Each transition replies with the selected `Language` (code + displayName) for that turn.

## Turn (transient request/response shape, not persisted)

Represented only in the endpoint/agent contract — see `contracts/`.
