package com.example.sanitizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import akka.javasdk.SanitizerContext;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class SensitiveFieldLogSanitizerTest {

  private static final String README_EXAMPLE =
      "User update {\"user\":\"u-42\",\"ssn\":\"123-45-6789\",\"dateOfBirth\":\"1990-01-31\"} password=hunter2";

  private static SensitiveFieldLogSanitizer sanitizerFor(String fields) {
    Config config = ConfigFactory.parseString("fields = " + fields);
    return new SensitiveFieldLogSanitizer(new SanitizerContext() {
      @Override
      public String name() {
        return "log-sensitive-fields";
      }

      @Override
      public Config config() {
        return config;
      }
    });
  }

  private final SensitiveFieldLogSanitizer sanitizer =
      sanitizerFor("[password, ssn, dateOfBirth, securityAnswer]");

  private String sanitize(String message) {
    var masked = sanitizer.sanitize(message);
    assertThat(masked).hasSameSizeAs(message);
    return masked;
  }

  @Test
  public void masksKeyValuePairValue() {
    assertThat(sanitize("password=hunter2")).isEqualTo("password=*******");
  }

  @Test
  public void masksJsonStringValue() {
    assertThat(sanitize("\"ssn\":\"123-45-6789\"")).isEqualTo("\"ssn\":\"***********\"");
  }

  @Test
  public void toleratesWhitespaceAroundJsonColon() {
    assertThat(sanitize("\"ssn\" : \"123-45-6789\"")).isEqualTo("\"ssn\" : \"***********\"");
    assertThat(sanitize("\"ssn\":\t \"x\"")).isEqualTo("\"ssn\":\t \"*\"");
  }

  @Test
  public void matchesFieldNamesCaseInsensitively() {
    assertThat(sanitize("PASSWORD=x1")).isEqualTo("PASSWORD=**");
    assertThat(sanitize("\"DateOfBirth\":\"1990-01-31\"")).isEqualTo("\"DateOfBirth\":\"**********\"");
  }

  @Test
  public void leavesUnconfiguredFieldsUntouched() {
    var message = "\"user\":\"u-42\" token=abc";
    assertThat(sanitizer.sanitize(message)).isSameAs(message);
  }

  @Test
  public void leavesPartialFieldNameMatchesUntouched() {
    var message = "oldpassword=x \"ssnHash\":\"y\" password_hint=z";
    assertThat(sanitizer.sanitize(message)).isSameAs(message);
  }

  @Test
  public void returnsSameInstanceWhenNothingMatches() {
    var message = "Akka Runtime started at 127.0.0.1:9000";
    assertThat(sanitizer.sanitize(message)).isSameAs(message);
  }

  @Test
  public void masksMultipleFieldsInOneLine() {
    assertThat(sanitize(README_EXAMPLE)).isEqualTo(
        "User update {\"user\":\"u-42\",\"ssn\":\"***********\",\"dateOfBirth\":\"**********\"} password=*******");
  }

  @Test
  public void masksKeyValueValueUpToSeparator() {
    assertThat(sanitize("user=u-42&password=hunter2&ssn=123-45-6789;next"))
        .isEqualTo("user=u-42&password=*******&ssn=***********;next");
  }

  @Test
  public void masksJsonValueContainingEscapedQuotes() {
    assertThat(sanitize("\"securityAnswer\":\"say \\\"hi\\\"\""))
        .isEqualTo("\"securityAnswer\":\"**********\"");
  }

  @Test
  public void doesNotThrowOnOddInput() {
    assertThat(sanitizer.sanitize(null)).isNull();
    Stream.of(
            "",
            "password=",
            "\"ssn\":",
            "\"ssn\":\"unterminated",
            "=\"=\"\"==\"\\\"password\"",
            "password=" + "x".repeat(10_000),
            "\"ssn\":\"" + "\\".repeat(5_001))
        .forEach(message -> assertThatCode(() -> sanitize(message)).doesNotThrowAnyException());
  }

  @Test
  public void leavesEmptyValuesUnchanged() {
    var message = "password= \"ssn\":\"\"";
    assertThat(sanitizer.sanitize(message)).isSameAs(message);
  }

  @Test
  public void emptyFieldListMasksNothing() {
    assertThat(sanitizerFor("[]").sanitize(README_EXAMPLE)).isSameAs(README_EXAMPLE);
  }
}
