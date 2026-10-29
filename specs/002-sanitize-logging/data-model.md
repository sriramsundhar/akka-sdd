# Data Model: Log-Only Data Sanitization Sample

No persisted state. The "model" is configuration plus fixed sample lines.

## Sanitizer entries (`src/main/resources/application.conf`)

```hocon
akka.javasdk.sanitization.sanitizers {
  "log-email"       { predefined = EMAIL,       apply-at = ["logs"] }
  "log-phone"       { predefined = PHONE,       apply-at = ["logs"] }
  "log-credit-card" { predefined = CREDIT_CARD, apply-at = ["logs"] }
  "log-iban"        { predefined = IBAN,        apply-at = ["logs"] }
  "log-ip-address"  { predefined = IP_ADDRESS,  apply-at = ["logs"] }
  "log-sensitive-fields" {
    class    = "com.example.sanitizer.SensitiveFieldLogSanitizer"
    apply-at = ["logs"]
    fields   = ["password", "ssn", "dateOfBirth", "securityAnswer"]
  }
}
```

Rules (checked by `SanitizationConfigScopeTest`):
- Every entry: `apply-at` is exactly `["logs"]`, with no `agents` or `agent-roles` keys.
- `log-sensitive-fields.fields`: a non-empty list of field names, matched case-insensitively.

## Sample log lines (demo + tests + README)

| kind | Input | Expected masked (observed, R4) |
|---|---|---|
| `email` | `Password reset requested for jane.doe@example.com` | `Password reset requested for ********************` |
| `phone` | `SMS verification sent to 202-555-0143` | `SMS verification sent to ************` |
| `credit-card` | `Payment declined for card 4111 1111 1111 1111` | `Payment declined for card *******************` |
| `iban` | `Refund issued to GB82WEST12345698765432` | `Refund issued to **********************` |
| `ip-address` | `Login from 192.0.2.10 and 2001:db8:0:0:0:0:0:1` | `Login from ********** and ********************` |
| `custom` | `User update {"user":"u-42","ssn":"123-45-6789","dateOfBirth":"1990-01-31"} password=hunter2` | `User update {"user":"u-42","ssn":"***********","dateOfBirth":"**********"} password=*******` |

`all` logs all six, in table order. The README must use output re-captured from the final implementation's real run.

## SensitiveFieldLogSanitizer behaviour

| Input | Output |
|---|---|
| `password=hunter2` | `password=*******` |
| `"ssn":"123-45-6789"` | `"ssn":"***********"` |
| `"ssn" : "123-45-6789"` | `"ssn" : "***********"` |
| `PASSWORD=x1`, `"DateOfBirth":"1990-01-31"` | `PASSWORD=**`, `"DateOfBirth":"**********"` |
| `"user":"u-42"` (unconfigured) | unchanged |
| `oldpassword=x`, `"ssnHash":"y"` (partial name) | unchanged |
| `password=` (empty) | unchanged |
| `"securityAnswer":"say \"hi\""` | value incl. escaped quotes masked, length preserved |
| no configured field | identical instance returned |
