# Task: Add a log-only data sanitization sample to this Akka SDK service

Implement a small, self-contained sample that demonstrates Akka's runtime data sanitization **for logs only**. Follow the official docs: <https://doc.akka.io/sdk/sanitization.html> (read it first if you have web access).

## Scope rules (important)

- Every sanitizer entry in this sample must use `apply-at = ["logs"]`. Do NOT apply anything at `model-call`, `tool-result` or `client`.
- Do NOT set `agents` or `agent-roles` on any entry — `logs` is a global application point and cannot be combined with them.
- Matched text is replaced by `*` characters of the same length as the match; don't write code that changes this for the predefined entries.
- Use only synthetic, obviously fake sample data (test card numbers, example.com emails, documentation IP ranges, fictional phone numbers). No real PII.

## Part 1 — One entry per predefined group

Add these to `src/main/resources/application.conf` under `akka.javasdk.sanitization.sanitizers`:

```hocon
akka.javasdk.sanitization.sanitizers {
  "log-email"       { predefined = EMAIL,       apply-at = ["logs"] }
  "log-phone"       { predefined = PHONE,       apply-at = ["logs"] }
  "log-credit-card" { predefined = CREDIT_CARD, apply-at = ["logs"] }
  "log-iban"        { predefined = IBAN,        apply-at = ["logs"] }
  "log-ip-address"  { predefined = IP_ADDRESS,  apply-at = ["logs"] }
}
```

For each one, the sample must log a realistic line that contains the sensitive value, and the README must show the expected before/after:

| Sanitizer | Sample log line (input) | Expected in logs |
| --- | --- | --- |
| EMAIL | `Password reset requested for jane.doe@example.com` | `Password reset requested for ********************` |
| PHONE | `SMS verification sent to +1 202 555 0143` | phone number fully masked with `*` |
| CREDIT_CARD | `Payment declined for card 4111 1111 1111 1111` | `Payment declined for card *******************` |
| IBAN | `Refund issued to GB82 WEST 1234 5698 7654 32` | IBAN fully masked with `*` |
| IP_ADDRESS | `Login from 192.0.2.10 and 2001:db8::1` | both addresses masked with `*` |

(Compute the exact expected mask lengths from the actual runtime output and put the real values in the README — don't guess.)

## Part 2 — One custom LogSanitizer (a case regex alone handles poorly)

**Use case:** structured log lines (JSON or `key=value`) carry sensitive *fields* whose values have no fixed format — e.g. `password`, `ssn`, `dateOfBirth`, `securityAnswer`. We want to keep the field name visible (useful for debugging) and mask only the value, with the list of field names driven by config.

Implement `com.example.sanitizer.SensitiveFieldLogSanitizer`:

- `implements akka.javasdk.LogSanitizer` (only this interface — not `TextSanitizer`).
- Constructor takes `akka.javasdk.SanitizerContext` and reads the field list with `context.config().getStringList("fields")`. No other dependency injection (not supported for LogSanitizers).
- Precompile the patterns in the constructor. It must handle both:
  - `key=value` → `password=hunter2` becomes `password=*******`
  - JSON → `"ssn":"123-45-6789"` becomes `"ssn":"***********"` (also tolerate whitespace around `:`)
- Field-name matching is case-insensitive; the mask length equals the value length.
- Return the message unchanged when nothing matches. Keep it fast — it runs on every log line of the service, including Akka's own. Never log from inside it, and never throw.

Register it:

```hocon
  "log-sensitive-fields" {
    class    = "com.example.sanitizer.SensitiveFieldLogSanitizer"
    apply-at = ["logs"]
    fields   = ["password", "ssn", "dateOfBirth", "securityAnswer"]
  }
```

Example for the README:

- Input: `User update {"user":"u-42","ssn":"123-45-6789","dateOfBirth":"1990-01-31"} password=hunter2`
- Expected: `User update {"user":"u-42","ssn":"***********","dateOfBirth":"**********"} password=*******`

## Part 3 — Demo endpoint to trigger the logs

Add `com.example.api.LogSanitizationDemoEndpoint`:

- `@HttpEndpoint("/log-sanitization-demo")`, `@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))` (demo only — note this in a comment).
- `@Get("/{kind}")` where `kind` is one of `email | phone | credit-card | iban | ip-address | custom | all`. It logs the matching sample line(s) from the tables above via SLF4J at INFO and returns a short plain-text confirmation (do not echo the raw sensitive value in the HTTP response).
- Unknown `kind` → 400.

## Part 4 — Tests

- Unit tests for `SensitiveFieldLogSanitizer` (construct it with a test `SanitizerContext`/config, or factor the masking logic so it's testable): key=value, JSON, whitespace variants, case-insensitive keys, unconfigured fields left untouched, no-match returns identical string, multiple fields in one line.
- Integration test extending `TestKitSupport`: use `getSanitizerClient()` and call `sanitize("<entry-name>", text)` for each of the five predefined entries, asserting the sensitive part is fully replaced by `*` of equal length and the surrounding text is unchanged. If the sanitizer client does not apply the class-based log-only entry, rely on the unit tests for it and say so in the README rather than changing its `apply-at`.

## Part 5 — README section

Add a short "Log sanitization sample" section covering: what's configured, the before/after table (with real observed output), how to run locally and hit `GET /log-sanitization-demo/all`, and the performance note from the docs (every `logs` entry runs on every log line — keep the number of rules and regex complexity low).

## Done when

- `mvn verify` passes.
- Running locally and calling `/log-sanitization-demo/all` shows every sensitive value masked in the console logs, with field names still visible for the custom case.
- No sanitizer entry in this sample applies anywhere other than `logs`.
