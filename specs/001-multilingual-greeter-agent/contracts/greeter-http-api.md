# Contract: Greeter HTTP API

Single HTTP endpoint, `@HttpEndpoint("/greetings")`, exposed for browser/programmatic clients (curl, quickstart demos).

## `POST /greetings/{sessionId}`

Sends one user turn to the greeter for the given session and gets back a rotating-language greeting. The `sessionId` path segment doubles as the Akka Agent session id (`ComponentClient.forAgent().inSession(sessionId)`) and the `SessionLanguageEntity` instance id — both are scoped to the same identifier, per Assumption in spec.md.

**Request body**:

```json
{
  "message": "Hi there!"
}
```

| Field | Type | Notes |
|---|---|---|
| `message` | `string` | The user's turn text. Content is accepted but never acted upon (FR-001) — only its arrival triggers the next rotation step. |

**Response body** (`200 OK`):

```json
{
  "language": "French",
  "greeting": "Bonjour !"
}
```

| Field | Type | Notes |
|---|---|---|
| `language` | `string` | Display name of the language selected for this turn (FR-007). |
| `greeting` | `string` | The greeting text only, in the selected language — nothing else (FR-001, SC-002). |

**Errors**:

| Status | When |
|---|---|
| `400 Bad Request` | Missing/blank `message`, or missing `sessionId` path segment. |
| `502 Bad Gateway` | The configured model provider call failed (e.g. no API key configured, provider outage). |

## Sequence per request

1. Endpoint receives the request, extracts `sessionId` and `message`.
2. Endpoint calls `SessionLanguageEntity(sessionId)::selectNextLanguage` (Key Value Entity, `ComponentClient.forKeyValueEntity(sessionId)`) → returns the `Language` (code + displayName) for this turn, per the state-transition rules in `data-model.md`.
3. Endpoint calls `GreeterAgent::greet` (`ComponentClient.forAgent().inSession(sessionId)`), passing the selected language's display name, so the model produces only a short greeting phrase in that language.
4. Endpoint returns `{language, greeting}` from steps 2–3.
