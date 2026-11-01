# Contract: Log Sanitization Demo Endpoint

`com.example.api.LogSanitizationDemoEndpoint`. `@HttpEndpoint("/log-sanitization-demo")`, `@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))`. Open to all callers for demo purposes only; this is stated in a code comment.

## `GET /log-sanitization-demo/{kind}`

| `kind` | Effect (SLF4J, INFO) | Status | Body (`text/plain`) |
|---|---|---|---|
| `email` \| `phone` \| `credit-card` \| `iban` \| `ip-address` | logs that kind's sample line | 200 | `Logged 1 sample line(s) for '<kind>'. Check the service logs.` |
| `custom` | logs both sensitive-field sample lines | 200 | `Logged 2 sample line(s) for 'custom'. Check the service logs.` |
| `all` | logs all 7 sample lines | 200 | `Logged 7 sample line(s) for 'all'. Check the service logs.` |
| anything else | nothing logged | 400 | `Unknown kind. Use one of: email, phone, credit-card, iban, ip-address, custom, all` |

Invariants:
- The response body never contains any raw sample value (checked by `LogSanitizationDemoEndpointIntegrationTest` against every raw sample value).
- The 400 body does not echo the requested `kind`.
- Returns `HttpResponse` via `HttpResponses.ok(...)` / `HttpResponses.badRequest(...)`; no exceptions are thrown for bad input.
