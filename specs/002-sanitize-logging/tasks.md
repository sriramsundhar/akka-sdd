---

description: "Task list for the log-only data sanitization sample"
---

# Tasks: Log-Only Data Sanitization Sample

**Input**: Design documents from `/specs/002-sanitize-logging/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/log-sanitization-demo.md, quickstart.md

**Tests**: Requested explicitly (spec FR-014, FR-015). Test tasks come before implementation in each story.

**Organization**: Grouped by user story (spec.md US1–US4).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1–US4 from spec.md
- Base paths: `src/main/java/com/example/`, `src/test/java/com/example/`, `src/main/resources/`

**Rules that apply to every task**:
- Every sanitizer entry: `apply-at = ["logs"]`; never `model-call`, `tool-result` or `client`; never `agents` or `agent-roles`.
- Synthetic data only, with the exact sample strings from data-model.md.
- Tests assert **exact** expected strings, not "contains `*`".

---

## Phase 1: Setup (SDK upgrade)

**Purpose**: Move to the SDK version that has log-only sanitizers (research R1, R2).

- [X] T001 Change the `akka-javasdk-parent` `<version>` from `3.6.6` to `3.7.0` in pom.xml. Leave the netty pins and the surefire override unchanged.
- [X] T002 Run `mvn verify` and confirm the existing tests (`Greeter*Test`, `LanguagePoolTest`, `WeatherAgentIntegrationTest`) still pass unchanged on 3.7.0 (pom.xml). If any fail, stop and report; don't edit greeter code to work around an SDK change without asking.
- [X] T003 In README.md, replace the local-run command `mvn compile exec:java -Dconfig.resource=application-dev.conf` with `AGENT_MODEL_PROVIDER=litellm SPIFFE_ENABLED=false mvn compile exec:java`. Explain briefly: on SDK 3.7.0 any `-Dconfig.resource` override fails at startup ("R2DBC database config scheme has changed"), and SPIFFE is on by default and needs a trust domain that local runs don't have. Keep the existing `application-dev.conf` paragraph about containers, but note that `-Dconfig.resource` doesn't start on 3.7.0.

**Checkpoint**: The service builds and existing tests pass on 3.7.0.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Guard the log-only scope rule before any entry is added (spec FR-002, research R7).

- [X] T004 Create src/test/java/com/example/sanitizer/SanitizationConfigScopeTest.java, a JUnit 5 + AssertJ unit test (no TestKit). Load `ConfigFactory.parseResources("application.conf")` (unresolved). If `akka.javasdk.sanitization.sanitizers` exists, iterate every entry in `.root().keySet()` and assert: `getStringList("apply-at")` equals `List.of("logs")`, and `hasPath("agents")` and `hasPath("agent-roles")` are both false. Test name: `everySanitizerEntryAppliesToLogsOnly`. Also add `noSanitizerEntryIsScopedToAgents` if it reads more clearly as two tests. Run `mvn -q test -Dtest=SanitizationConfigScopeTest`.

**Checkpoint**: The scope guard exists; user stories can begin.

---

## Phase 3: User Story 1 - Common PII is masked in logs (Priority: P1) 🎯 MVP

**Goal**: Five predefined log-only entries mask email, phone, card, IBAN and IP values with equal-length `*`.

**Independent Test**: `getSanitizerClient().sanitize("<entry>", line)` returns the exact masked line for each of the five samples. A JSON-format local run masks them in the logs.

### Tests for User Story 1

- [X] T005 [P] [US1] Create src/test/java/com/example/sanitizer/PredefinedLogSanitizersIntegrationTest.java extending `TestKitSupport`. Add one `@Test` per entry, calling `getSanitizerClient().sanitize(name, input)` and asserting `isEqualTo` the exact output:
  - `log-email`: `Password reset requested for jane.doe@example.com` → `Password reset requested for ********************`
  - `log-phone`: `SMS verification sent to 202-555-0143` → `SMS verification sent to ************`
  - `log-credit-card`: `Payment declined for card 4111 1111 1111 1111` → `Payment declined for card *******************`
  - `log-iban`: `Refund issued to GB82WEST12345698765432` → `Refund issued to **********************`
  - `log-ip-address`: `Login from 192.0.2.10 and 2001:db8:0:0:0:0:0:1` → `Login from ********** and ********************`

  Also assert `output.length() == input.length()`. Keep the inputs as local constants in the test; don't import them from the endpoint. Run `mvn -q verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=PredefinedLogSanitizersIntegrationTest` and confirm it fails because the entries don't exist yet.

### Implementation for User Story 1

- [X] T006 [US1] Append an `akka.javasdk.sanitization.sanitizers { … }` block to src/main/resources/application.conf with the five entries `log-email` (EMAIL), `log-phone` (PHONE), `log-credit-card` (CREDIT_CARD), `log-iban` (IBAN) and `log-ip-address` (IP_ADDRESS), each `apply-at = ["logs"]`, exactly as in data-model.md. Add a short comment above the block: log-only, masked in the JSON log format, and every entry runs on every log line.
- [X] T007 [US1] In src/test/java/com/example/sanitizer/SanitizationConfigScopeTest.java, add `predefinedLogEntriesAreConfigured`. It asserts the five entry names exist and that each `getString("predefined")` equals EMAIL, PHONE, CREDIT_CARD, IBAN and IP_ADDRESS respectively.
- [X] T008 [US1] Run `mvn -q test -Dtest=SanitizationConfigScopeTest` and the T005 `verify` command; both pass (src/test/java/com/example/sanitizer/PredefinedLogSanitizersIntegrationTest.java).

**Checkpoint**: US1 is complete and testable on its own (the MVP).

---

## Phase 4: User Story 2 - Sensitive fields masked, names kept (Priority: P2)

**Goal**: A config-driven custom `LogSanitizer` masks the values of `password`, `ssn`, `dateOfBirth` and `securityAnswer` in JSON and `key=value` text.

**Independent Test**: Unit tests on the class directly, plus `getSanitizerClient().sanitize("log-sensitive-fields", …)` on the README example.

### Tests for User Story 2

- [X] T009 [P] [US2] Create src/test/java/com/example/sanitizer/SensitiveFieldLogSanitizerTest.java, a plain JUnit 5 + AssertJ test. Build the sanitizer with a test `SanitizerContext` implemented as an anonymous class or lambda pair: `name()` returns `"log-sensitive-fields"`, and `config()` returns `ConfigFactory.parseString("fields = [password, ssn, dateOfBirth, securityAnswer]")`. Each test has a behavior-describing name:
  - `masksKeyValuePairValue`: `password=hunter2` → `password=*******`
  - `masksJsonStringValue`: `"ssn":"123-45-6789"` → `"ssn":"***********"`
  - `toleratesWhitespaceAroundJsonColon`: `"ssn" : "123-45-6789"` → `"ssn" : "***********"`, and `"ssn":  "x"` (tab/spaces) masked
  - `matchesFieldNamesCaseInsensitively`: `PASSWORD=x1` → `PASSWORD=**`; `"DateOfBirth":"1990-01-31"` → `"DateOfBirth":"**********"`
  - `leavesUnconfiguredFieldsUntouched`: `"user":"u-42" token=abc` is unchanged
  - `leavesPartialFieldNameMatchesUntouched`: `oldpassword=x "ssnHash":"y"` is unchanged
  - `returnsSameInstanceWhenNothingMatches`: use `isSameAs`
  - `masksMultipleFieldsInOneLine`: the README example `User update {"user":"u-42","ssn":"123-45-6789","dateOfBirth":"1990-01-31"} password=hunter2` → `User update {"user":"u-42","ssn":"***********","dateOfBirth":"**********"} password=*******`
  - `masksJsonValueContainingEscapedQuotes`: `"securityAnswer":"say \"hi\""` → the value is fully masked and the length is preserved
  - `doesNotThrowOnOddInput`: `null` (returned as-is), `""`, `password=`, `"ssn":`, `"ssn":"unterminated`, a 10 000-char line, and `=` / `"` soup all return without an exception
  - `maskPreservesLength`: for every case above, `output.length() == input.length()`
  - `emptyFieldListMasksNothing`: with `fields = []`, the README example is returned as the same instance

  Run `mvn -q test -Dtest=SensitiveFieldLogSanitizerTest` and confirm it fails to compile or fails, because the class doesn't exist yet.

### Implementation for User Story 2

- [X] T010 [US2] Create src/main/java/com/example/sanitizer/SensitiveFieldLogSanitizer.java: `public final class … implements akka.javasdk.LogSanitizer` (only that interface), with a single public constructor `(akka.javasdk.SanitizerContext context)`. In the constructor, read `context.config().getStringList("fields")`, build `names = fields.stream().map(Pattern::quote).collect(joining("|"))`, and precompile with `CASE_INSENSITIVE`:
  - JSON: `"(" + names + ")"(\s*:\s*)"((?:[^"\\]|\\.)*)"`, which masks group 3
  - KV: `(?<![A-Za-z0-9_])(" + names + ")=([^\s&,;]+)`, which masks group 2

  If the list is empty, leave both patterns null. In `sanitize(String message)`:
  - Return `message` if it's null or there are no patterns.
  - For each pattern, `matcher.find()`. If there's no match, keep the current string. Otherwise rebuild with a `StringBuilder`, copying the text between matches and replacing only the value group with `"*".repeat(len)`.
  - Return the original instance when neither pattern matched.

  No logging, no try/catch, no static mutable state. Add a short class Javadoc: runs on every log line; masks values and keeps field names. Run the T009 tests until they pass.
- [X] T011 [US2] Add the `log-sensitive-fields` entry to the sanitizers block in src/main/resources/application.conf: `class = "com.example.sanitizer.SensitiveFieldLogSanitizer"`, `apply-at = ["logs"]`, `fields = ["password", "ssn", "dateOfBirth", "securityAnswer"]`.
- [X] T012 [US2] In src/test/java/com/example/sanitizer/SanitizationConfigScopeTest.java, add `sensitiveFieldEntryIsConfigured`. It asserts that `class` equals `com.example.sanitizer.SensitiveFieldLogSanitizer` and that `fields` equals the four names.
- [X] T013 [US2] In src/test/java/com/example/sanitizer/PredefinedLogSanitizersIntegrationTest.java, add `sensitiveFieldEntryMasksValuesByName`. It calls `sanitize("log-sensitive-fields", <README example>)` and asserts the exact masked output (research R5: the client reaches class-based log-only entries).
- [X] T014 [US2] Run `mvn -q test -Dtest='SensitiveFieldLogSanitizerTest,SanitizationConfigScopeTest'` and the IT `verify` command from T005; all pass (src/test/java/com/example/sanitizer/).

**Checkpoint**: US1 and US2 both pass on their own.

---

## Phase 5: User Story 3 - Trigger the demo on demand (Priority: P3)

**Goal**: `GET /log-sanitization-demo/{kind}` logs sample lines and never echoes them (contracts/log-sanitization-demo.md).

**Independent Test**: An HTTP integration test checks statuses and bodies for all kinds and for an unknown kind.

### Tests for User Story 3

- [X] T015 [P] [US3] Create src/test/java/com/example/api/LogSanitizationDemoEndpointIntegrationTest.java extending `TestKitSupport` and using `httpClient` (not `componentClient`).
  - Parameterize over `email, phone, credit-card, iban, ip-address, custom, all` (or one test with a loop). Each `GET /log-sanitization-demo/{kind}` with `responseBodyAs(String.class)` returns 200, and the body contains none of these raw values: `jane.doe@example.com`, `202-555-0143`, `4111 1111 1111 1111`, `GB82WEST12345698765432`, `192.0.2.10`, `2001:db8:0:0:0:0:0:1`, `123-45-6789`, `1990-01-31`, `hunter2`.
  - `all` returns a body that mentions 6 lines.
  - `GET /log-sanitization-demo/unknown` returns 400 (`invoke()` throws for non-2xx, so assert on the thrown exception or use the raw response API per `akka-context/sdk/http-endpoints.html.md`), and the body doesn't contain `unknown`.

  Run `mvn -q verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=LogSanitizationDemoEndpointIntegrationTest` and confirm it fails.

### Implementation for User Story 3

- [X] T016 [US3] Create src/main/java/com/example/api/LogSanitizationDemoEndpoint.java with `@HttpEndpoint("/log-sanitization-demo")` and `@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))`, plus the comment `// Demo only: open to all callers so the sample can be triggered with curl. Do not copy this ACL to real endpoints.`. No `@Component`.
  - `private static final Logger log = LoggerFactory.getLogger(LogSanitizationDemoEndpoint.class)`.
  - Six `private static final String` sample lines, exactly as in data-model.md.
  - `@Get("/{kind}") public HttpResponse logSample(String kind)`: a `switch` maps each kind to `List.of(line)`, `all` maps to all six in table order, and the default returns `HttpResponses.badRequest("Unknown kind. Use one of: email, phone, credit-card, iban, ip-address, custom, all")`.
  - Log each line with `log.info(line)`, then return `HttpResponses.ok("Logged " + n + " sample line(s) for '" + kind + "'. Check the service logs.")`.
- [X] T017 [US3] Run the T015 `verify` command; it passes (src/test/java/com/example/api/LogSanitizationDemoEndpointIntegrationTest.java).

**Checkpoint**: The demo trigger works; US1–US3 are independently green.

---

## Phase 6: User Story 4 - Documented before/after (Priority: P3)

**Goal**: The README shows the real observed output, how to run the demo, the known gaps, and the performance note.

**Independent Test**: Run the demo, then compare the JSON `message` fields with the README table character for character.

- [X] T018 [US4] Run locally with the production log format: `SPIFFE_ENABLED=false mvn compile exec:java -Dlogback.configurationFile=logback.xml` (in the background, from the repo root). Once `Akka Runtime started` appears, `curl -s localhost:9000/log-sanitization-demo/all`, then stop the service. Capture the six `"message"` values from the JSON log lines into a scratchpad file. If any value isn't fully masked, stop and report rather than editing masks. Also capture the masked `Akka Runtime started at …` line. (Output: scratchpad notes; nothing is written to the repo.)
- [X] T019 [US4] Add a "Log sanitization sample" section to README.md, after "Running with Postgres". It covers:
  - What's configured: the six entries, all `apply-at = ["logs"]`, and the custom class with its `fields` list.
  - A before/after table pasted from the T018 capture (exact strings).
  - How to run: the T018 command plus `curl localhost:9000/log-sanitization-demo/all`, and that the other kinds are `email|phone|credit-card|iban|ip-address|custom`.
  - Why `-Dlogback.configurationFile=logback.xml`: the dev-mode console format isn't sanitized, while the platform and standalone JSON logs are.
  - Known format gaps from research R4: `+1 202 555 0143` → `+1 ************`; a spaced IBAN isn't masked; `2001:db8::1` → `**********1`.
  - Rules also mask the runtime's own lines (e.g. `Akka Runtime started at *********:9000`).
  - Tests: the sanitizer client covers all six entries by name, and the custom class also has unit tests.
  - Performance: every `logs` entry runs on every log line, including Akka's own, so keep the number of rules and regex complexity low, and keep custom log sanitizers fast, non-logging and non-throwing.

**Checkpoint**: All four stories are done.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T020 Run the full `mvn verify` (pom.xml); everything passes, including greeter tests and the four new test classes.
- [X] T021 [P] Confirm the scope across src/main/resources/application.conf: `grep -n "apply-at\|agents\|agent-roles" src/main/resources/application.conf` shows only `["logs"]` for the six entries and no agent scoping. `SanitizationConfigScopeTest` enforces this, so this is a visual double-check.
- [X] T022 [P] Self-review src/main/java/com/example/sanitizer/SensitiveFieldLogSanitizer.java and src/main/java/com/example/api/LogSanitizationDemoEndpoint.java against the AGENTS.md checklist: `@Acl` present, no `@Component` on the endpoint, synchronous style, `akka.*` imports, no logging inside the sanitizer.
- [X] T023 Walk through specs/002-sanitize-logging/quickstart.md end to end and fix any drift between it and the README.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (T001–T003)**: first; T002 depends on T001.
- **Foundational (T004)**: depends on T001; blocks all stories.
- **US1 (T005–T008)**, **US2 (T009–T014)** and **US3 (T015–T017)** each depend only on Foundational.
  - Same-file edits: `application.conf` (T006, T011), `SanitizationConfigScopeTest` (T007, T012) and `PredefinedLogSanitizersIntegrationTest` (T013 after T005) must run in order: US1 before US2.
- **US4 (T018–T019)**: depends on US1–US3, since it captures real output from all six kinds.
- **Polish (T020–T023)**: after all stories.

### User Story Dependencies

- **US1 (P1)**: independent (MVP).
- **US2 (P2)**: independent in behaviour. It shares three files with US1, so do it after US1 or merge carefully.
- **US3 (P3)**: fully independent: the endpoint tests check only HTTP behaviour.
- **US4 (P3)**: needs US1–US3.

### Within Each User Story

- Write the tests first and see them fail; then implement; then run the story's test command.

---

## Parallel Execution Examples

### User Story 1

```bash
Task: "T005 PredefinedLogSanitizersIntegrationTest in src/test/java/com/example/sanitizer/"
# T006/T007 follow sequentially (shared files)
```

### User Story 2

```bash
Task: "T009 SensitiveFieldLogSanitizerTest in src/test/java/com/example/sanitizer/"
# in parallel with US3's tests:
Task: "T015 LogSanitizationDemoEndpointIntegrationTest in src/test/java/com/example/api/"
```

### User Story 3

```bash
Task: "T015 LogSanitizationDemoEndpointIntegrationTest"   # parallel with T009 / T010
Task: "T016 LogSanitizationDemoEndpoint"                 # after T015, independent of US1/US2 files
```

### User Story 4

Sequential (T018 → T019).

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Setup (T001–T003) → Foundational (T004)
2. US1 (T005–T008) → validate with the sanitizer-client IT → **stop and review**

### Incremental Delivery

1. + US2: custom field sanitizer
2. + US3: demo endpoint
3. + US4: README with real captured output
4. Polish: full `mvn verify`

Per CLAUDE.md, pause for user approval after each component and its test.

---

## Notes

- Exit-condition checks (advisory in À la carte) map to T004/T007/T012, T005/T013, T009, T015, and T020.
- Don't change predefined masking behaviour; if output differs from data-model.md, report it.
- Never widen `apply-at` to make something testable.

## Follow-up (after user feedback)

- A plain `mvn clean compile exec:java` showed unmasked logs, because the dev console format doesn't sanitize. Fix: `pom.xml` now sets `logback.configurationFile` to `logback.xml` (JSON, sanitized) by default. README, quickstart, research R2/R3 and plan were updated.
- Correction: `SPIFFE_ENABLED=false` is not needed for plain local runs (T003/T018 wording above is superseded). The SPIFFE failure only occurs together with a `-Dconfig.resource` override.
