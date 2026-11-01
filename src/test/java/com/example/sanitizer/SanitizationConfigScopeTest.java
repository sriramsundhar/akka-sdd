package com.example.sanitizer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/** Every sanitizer entry in this service must mask log output only. */
public class SanitizationConfigScopeTest {

  private static final String SANITIZERS = "akka.javasdk.sanitization.sanitizers";

  private final Config config = ConfigFactory.parseResources("application.conf");

  private List<Config> entries() {
    if (!config.hasPath(SANITIZERS)) return List.of();
    var sanitizers = config.getConfig(SANITIZERS);
    return sanitizers.root().keySet().stream()
        .map(name -> sanitizers.getConfig("\"" + name + "\""))
        .toList();
  }

  @Test
  public void everySanitizerEntryAppliesToLogsOnly() {
    assertThat(entries())
        .allSatisfy(entry -> assertThat(entry.getStringList("apply-at")).containsExactly("logs"));
  }

  @Test
  public void predefinedLogEntriesAreConfigured() {
    var sanitizers = config.getConfig(SANITIZERS);
    assertThat(Map.of(
            "log-email", "EMAIL",
            "log-phone", "PHONE",
            "log-credit-card", "CREDIT_CARD",
            "log-iban", "IBAN",
            "log-ip-address", "IP_ADDRESS"))
        .allSatisfy((name, group) ->
            assertThat(sanitizers.getString("\"" + name + "\".predefined")).isEqualTo(group));
  }

  @Test
  public void sensitiveFieldEntryIsConfigured() {
    var entry = config.getConfig(SANITIZERS + ".\"log-sensitive-fields\"");
    assertThat(entry.getString("class")).isEqualTo(SensitiveFieldLogSanitizer.class.getName());
    assertThat(entry.getStringList("fields"))
        .containsExactly("password", "ssn", "dateOfBirth", "securityAnswer");
  }

  @Test
  public void noSanitizerEntryIsScopedToAgents() {
    assertThat(entries())
        .allSatisfy(entry -> {
          assertThat(entry.hasPath("agents")).isFalse();
          assertThat(entry.hasPath("agent-roles")).isFalse();
        });
  }
}
