# Quickstart: Log-Only Data Sanitization Sample

## Tests

```shell
mvn verify   # unit + integration (SanitizationConfigScopeTest, SensitiveFieldLogSanitizerTest,
             # PredefinedLogSanitizersIntegrationTest, LogSanitizationDemoEndpointIntegrationTest)
```

## See masking in real logs

The SDK's dev-mode console format is **not** sanitized, so `pom.xml` sets `logback.configurationFile` to the runtime's JSON format (`logback.xml`), which is:

```shell
mvn compile exec:java
curl localhost:9000/log-sanitization-demo/all
```

Expected `message` fields in the JSON log lines:

```
Password reset requested for ********************
SMS verification sent to ************
Payment declined for card *******************
Refund issued to **********************
Login from ********** and ********************
User update {"user":"u-42","ssn":"***********","dateOfBirth":"**********"} password=*******
```

Add `-Dlogback.configurationFile=logback-runtime-dev-mode.xml` for the readable console format; the same lines then appear unmasked.
