# Quickstart: Log-Only Data Sanitization Sample

## Tests

```shell
mvn verify   # unit + integration (SanitizationConfigScopeTest, SensitiveFieldLogSanitizerTest,
             # PredefinedLogSanitizersIntegrationTest, LogSanitizationDemoEndpointIntegrationTest)
```

## See masking in real logs

The dev-mode console format is **not** sanitized. Run with the runtime's production (JSON) log format:

```shell
SPIFFE_ENABLED=false mvn compile exec:java -Dlogback.configurationFile=logback.xml
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

`SPIFFE_ENABLED=false` is needed for local runs on SDK 3.7.0 (see research R2).
