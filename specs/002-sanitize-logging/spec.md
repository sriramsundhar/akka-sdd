# Feature Specification: Log-Only Data Sanitization Sample

**Feature Branch**: `002-sanitize-logging`
**Created**: 2026-10-28
**Status**: Draft
**Input**: User description: "Add a log-only data sanitization sample to this Akka SDK service: one log-only masking rule per predefined sensitive-data group (email, phone, credit card, IBAN, IP address), one custom configurable rule that masks the values of named sensitive fields in structured log lines while keeping field names visible, a demo trigger that writes realistic sample log lines, tests, and a README section with real before/after output. Every rule applies to logs only."

## User Scenarios & Testing *(mandatory)*

The audience for this sample is a developer or operator evaluating how the service keeps personal data out of its logs.

### User Story 1 - Common PII is masked in logs (Priority: P1)

A developer triggers the demo and watches the service's console log. Each sample line contains one kind of common personal data (an email address, a phone number, a card number, a bank account number, or IP addresses). In the log output, every one of those values appears as a run of `*` characters of exactly the same length, while the rest of the line reads normally.

**Why this priority**: This is the core promise of the sample — sensitive values never reach the logs, using only built-in detection with no custom code.

**Independent Test**: Trigger the demo for one category (e.g. email) and confirm the console line shows the surrounding text intact and the value replaced by an equal-length mask.

**Acceptance Scenarios**:

1. **Given** the email rule is enabled, **When** the line `Password reset requested for jane.doe@example.com` is logged, **Then** the log shows `Password reset requested for ********************` (20 `*`).
2. **Given** the phone rule is enabled, **When** `SMS verification sent to 202-555-0143` is logged, **Then** the log shows `SMS verification sent to ************` (12 `*`).
3. **Given** the card rule is enabled, **When** `Payment declined for card 4111 1111 1111 1111` is logged, **Then** the log shows `Payment declined for card *******************` (19 `*`).
4. **Given** the bank-account rule is enabled, **When** `Refund issued to GB82WEST12345698765432` is logged, **Then** the log shows `Refund issued to **********************` (22 `*`).
5. **Given** the IP rule is enabled, **When** `Login from 192.0.2.10 and 2001:db8:0:0:0:0:0:1` is logged, **Then** the log shows `Login from ********** and ********************` (10 and 20 `*`).
6. **Given** the sample formats originally requested (`+1 202 555 0143`, spaced IBAN `GB82 WEST 1234 5698 7654 32`, compressed IPv6 `2001:db8::1`), **When** they are sanitized, **Then** they are only partly masked or not masked at all (observed: `+1 ************`, IBAN untouched, `**********1`). The sample uses formats the built-in groups mask fully, and the README documents these gaps.

---

### User Story 2 - Sensitive fields in structured log lines are masked, names kept (Priority: P2)

A developer logs a structured line (JSON-style or `key=value`) that carries sensitive fields whose values have no fixed format — password, SSN, date of birth, security answer. In the log output the field *names* remain visible for debugging, but each configured field's *value* is replaced by `*` of the same length. The list of field names is set in configuration, not code.

**Why this priority**: Built-in groups cannot recognise free-form secrets; this shows how to extend the policy for a real-world case while keeping logs debuggable.

**Independent Test**: Trigger the demo's custom case and confirm the logged line matches the expected masked form exactly.

**Acceptance Scenarios**:

1. **Given** fields `password, ssn, dateOfBirth, securityAnswer` are configured, **When** `User update {"user":"u-42","ssn":"123-45-6789","dateOfBirth":"1990-01-31"} password=hunter2` is logged, **Then** the log shows `User update {"user":"u-42","ssn":"***********","dateOfBirth":"**********"} password=*******`.
2. **Given** a `key=value` pair such as `password=hunter2`, **When** it is logged, **Then** it appears as `password=*******`.
3. **Given** a JSON pair with whitespace around the colon (e.g. `"ssn" : "123-45-6789"`), **When** it is logged, **Then** the value is masked and the name, quotes and whitespace are unchanged.
4. **Given** a field name in different letter case (e.g. `PASSWORD=x1`, `"DateOfBirth":"…"`), **When** it is logged, **Then** the value is masked.
5. **Given** a field that is not configured (e.g. `"user":"u-42"`), **When** it is logged, **Then** it is left untouched.
6. **Given** a line with no configured fields, **When** it is logged, **Then** the line is identical to the original.

---

### User Story 3 - Trigger the demo on demand (Priority: P3)

A developer running the service locally requests the demo for a single category or for all categories at once, and gets a short confirmation. The confirmation never echoes the raw sensitive value. Requesting an unknown category is rejected as a bad request.

**Why this priority**: Makes the sample easy to explore, but the masking (US1, US2) delivers the value on its own.

**Independent Test**: Request each category and `all`; check the confirmation text and that an unknown category is rejected.

**Acceptance Scenarios**:

1. **Given** the service is running, **When** the developer requests category `all`, **Then** all seven sample lines (five predefined + two custom) are logged and a short confirmation is returned.
2. **Given** the service is running, **When** the developer requests one of `email`, `phone`, `credit-card`, `iban`, `ip-address`, `custom`, **Then** only that category's sample line(s) are logged (one line each; two for `custom`).
3. **Given** the service is running, **When** the developer requests an unknown category, **Then** the request is rejected as a bad request and nothing sensitive is logged.
4. **Given** any successful request, **When** the confirmation is returned, **Then** it contains none of the raw sample values.

---

### User Story 4 - Documented before/after (Priority: P3)

A reader of the project README finds a short section explaining what is masked, a before/after table showing the exact output observed from a real run, how to run the demo, and a performance caution.

**Why this priority**: Documentation multiplies the sample's value but depends on US1–US3 being in place.

**Independent Test**: Run the demo, compare console output to the README table character for character.

**Acceptance Scenarios**:

1. **Given** the README section, **When** a reader runs the demo locally, **Then** every "after" value in the table matches the console output exactly.
2. **Given** the README section, **When** a reader looks for the performance caution, **Then** it states that every log-only rule runs on every log line and advises keeping rule count and pattern complexity low.

### Edge Cases

- **Overlapping rules**: all log-only rules run on every log line, so a line can be touched by more than one rule (e.g. the phone rule may also match digit runs inside a card number, IBAN, SSN or date). The masked result must still hide every sensitive value with equal-length masks; if a rule masks more than its intended value on a real run, the README records the actual observed output rather than an idealised one.
- **Non-sample log lines**: the rules also apply to the service's own and the runtime's log lines. A custom-rule failure must never break or suppress logging; the custom rule must never raise an error and must never write log output itself.
- **Empty or unusual values**: an empty value (`password=`), a value at the end of the line, or a JSON value containing escaped quotes must not cause an error; the line is returned either masked or unchanged.
- **Field-name boundaries**: a configured name embedded in a longer name (e.g. `oldpassword=…`, `"ssnHash":…`) — matching is on whole field names only; partial names are left untouched.
- **Mixed formats in one line**: JSON-style and `key=value` pairs in the same line are both masked (see US2, Scenario 1).
- **Unknown demo category**: rejected as a bad request (see US3, Scenario 3).
- **Where masking is visible**: masking happens in the service's production (JSON) log format, the one the Akka platform and standalone image use. The plain-text dev-mode console format, used by default for local runs and tests, does not mask. The local demo must therefore be run with the production log format, and the README must say so.
- **Runtime lines are masked too**: e.g. the IP rule masks the runtime's own bind address in `Kalix Runtime started at *********:9000`. This is expected for a global logs rule and is documented.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The service MUST enable exactly one log-only masking rule for each predefined sensitive-data group: email, phone, credit card, IBAN, IP address.
- **FR-002**: Every masking rule introduced by this sample MUST apply to log output only — never to model requests, tool results, or ad-hoc application calls — and MUST NOT be scoped to specific agents or agent roles.
- **FR-003**: Masked text MUST be replaced by `*` characters, one per masked character, so the mask length equals the original value length.
- **FR-004**: The service MUST provide a custom log-only rule that masks the values of configured sensitive field names in both JSON-style (`"name":"value"`, whitespace tolerated around the colon) and `key=value` log text, leaving field names, quotes and separators visible.
- **FR-005**: The list of sensitive field names for the custom rule MUST come from configuration; the sample configures `password`, `ssn`, `dateOfBirth`, `securityAnswer`.
- **FR-006**: Field-name matching in the custom rule MUST be case-insensitive and on whole field names only.
- **FR-007**: The custom rule MUST return the line unchanged when no configured field is present, MUST NOT raise errors for any input, and MUST NOT write any log output itself.
- **FR-008**: The custom rule MUST do its pattern preparation once at start-up, not per log line.
- **FR-009**: The service MUST expose a demo trigger accepting categories `email`, `phone`, `credit-card`, `iban`, `ip-address`, `custom`, `all`, which writes the corresponding sample line(s) at INFO level and returns a short plain-text confirmation.
- **FR-010**: The demo trigger's response MUST NOT contain any raw sample sensitive value.
- **FR-011**: The demo trigger MUST reject an unknown category as a bad request.
- **FR-012**: The demo trigger MAY be open to all callers because it is a demo; this MUST be stated in a code comment next to its access rule.
- **FR-013a**: Sample values MUST use formats that the built-in groups mask completely (national phone `202-555-0143`, compact IBAN, fully expanded IPv6). Partial-mask formats MUST be listed in the README as known gaps.
- **FR-013**: All sample data MUST be synthetic and obviously fake (test card numbers, `example.com` addresses, documentation IP ranges, fictional phone numbers, IBAN check-digit examples).
- **FR-014**: Automated tests MUST verify each of the five predefined rules by name: the sensitive part is fully replaced by `*` of equal length and the surrounding text is unchanged.
- **FR-015**: Automated tests MUST verify the custom rule for: `key=value`, JSON, whitespace variants, case-insensitive names, unconfigured fields untouched, no-match returns an identical string, multiple fields in one line. If the named-rule test facility does not apply the custom log-only rule, the custom rule is covered by these unit tests alone, and the README says so (its scope is not widened to make it testable).
- **FR-016**: The README MUST gain a "Log sanitization sample" section with: what is configured, a before/after table using output observed from a real run (exact mask lengths, not estimates), how to run locally with the production log format and request `all`, the known format gaps, and the performance caution.
- **FR-017**: The full build including all tests MUST pass.

### Constraints carried from the request

These identifiers are mandated by the requester (not design choices) and are recorded so planning keeps them:

- Rule names: `log-email`, `log-phone`, `log-credit-card`, `log-iban`, `log-ip-address`, `log-sensitive-fields`.
- Custom rule: `com.example.sanitizer.SensitiveFieldLogSanitizer`, a log-only sanitizer constructed from its sanitizer context only, reading `fields` from its config.
- Demo trigger: `com.example.api.LogSanitizationDemoEndpoint`, `GET /log-sanitization-demo/{kind}`.

### Key Entities

- **Masking rule**: a named policy entry that identifies sensitive text in log lines and masks it. Attributes: name, kind (predefined group or custom), where it applies (logs only), rule-specific settings (e.g. field names).
- **Sensitive field list**: the configured set of field names whose values the custom rule masks.
- **Sample log line**: a fixed, synthetic line per category used by the demo and documented in the README with its observed masked output.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For all 7 sample lines, 100% of sensitive values are masked in the log output (production log format) of a local run of the `all` demo, with 0 raw sample values visible.
- **SC-002**: In every masked sample line, each mask has exactly the same character count as the value it replaces, and all non-sensitive text (including field names in the custom case) is character-for-character unchanged.
- **SC-003**: 6 of 6 rules apply to log output only; 0 rules apply to model requests, tool results, or ad-hoc calls, and 0 are scoped to agents or roles.
- **SC-004**: The full automated build passes with every listed test scenario (5 predefined-rule checks, ≥7 custom-rule scenarios) passing.
- **SC-005**: Every "after" value in the README table matches a real run's output exactly (0 mismatches).
- **SC-006**: 0 demo responses contain a raw sample value; an unknown category is rejected 100% of the time.

## Assumptions

- **SDK upgrade required**: the service is currently on Akka SDK 3.6.6, which does not provide named log-only sanitizer entries, custom log sanitizers, sanitizer context, or the named-sanitizer test client. These first appear in 3.7.0 (available in the local Maven cache). The sample assumes the service is upgraded to SDK 3.7.0; the existing greeter-agent feature must still build and pass its tests after the upgrade.
- The bundled offline Akka docs (`akka-context/`) describe the older configuration style; the online documentation at doc.akka.io is authoritative for this feature.
- The sample formats were adjusted from the request after probing SDK 3.7.0 (see FR-013a). The requested outcome, every value fully masked, takes precedence over the literal sample strings.
- Rules are added to the deployment configuration, so the local-development configuration (which includes it) inherits them unchanged.
- "Whole field name" matching means the name is not preceded or followed by a letter, digit or underscore; for `key=value`, the value runs until whitespace, `&`, `,`, `;` or end of line.
- The custom rule only masks string-valued JSON fields (`"name":"value"`); unquoted JSON values (numbers, booleans) for configured names are out of scope.
- The demo trigger is for local demonstration; no authentication is added.
- If the named-rule test facility *does* apply the custom log-only rule, an additional integration check for it is added; otherwise unit tests cover it (FR-015).

## Dependencies

- Akka SDK 3.7.0 (see Assumptions).
- Local run of the service to capture real masked output for the README.
