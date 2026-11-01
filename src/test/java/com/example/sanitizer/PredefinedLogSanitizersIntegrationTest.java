package com.example.sanitizer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.TestKitSupport;

/**
 * Exercises each log-only sanitizer entry by name. Log lines written during tests use the dev-mode
 * console format, which is not sanitized, so the sanitizer client is how masking is verified here.
 */
public class PredefinedLogSanitizersIntegrationTest extends TestKitSupport {

  private String sanitize(String entry, String text) {
    var masked = getSanitizerClient().sanitize(entry, text);
    assertThat(masked).hasSameSizeAs(text);
    return masked;
  }

  @Test
  public void emailEntryMasksEmailAddress() {
    assertThat(sanitize("log-email", "Password reset requested for jane.doe@example.com"))
        .isEqualTo("Password reset requested for ********************");
  }

  @Test
  public void phoneEntryMasksPhoneNumber() {
    assertThat(sanitize("log-phone", "SMS verification sent to 202-555-0143"))
        .isEqualTo("SMS verification sent to ************");
  }

  @Test
  public void creditCardEntryMasksCardNumber() {
    assertThat(sanitize("log-credit-card", "Payment declined for card 4111 1111 1111 1111"))
        .isEqualTo("Payment declined for card *******************");
  }

  @Test
  public void ibanEntryMasksIban() {
    assertThat(sanitize("log-iban", "Refund issued to GB82WEST12345698765432"))
        .isEqualTo("Refund issued to **********************");
  }

  @Test
  public void ipAddressEntryMasksIpv4AndIpv6() {
    assertThat(sanitize("log-ip-address", "Login from 192.0.2.10 and 2001:db8:0:0:0:0:0:1"))
        .isEqualTo("Login from ********** and ********************");
  }

  @Test
  public void sensitiveFieldEntryMasksValuesByName() {
    assertThat(sanitize("log-sensitive-fields",
            "User update {\"user\":\"u-42\",\"ssn\":\"123-45-6789\",\"dateOfBirth\":\"1990-01-31\"} password=hunter2"))
        .isEqualTo(
            "User update {\"user\":\"u-42\",\"ssn\":\"***********\",\"dateOfBirth\":\"**********\"} password=*******");
  }

  @Test
  public void sensitiveFieldEntryMasksSecurityAnswerWithSpacesAroundColon() {
    assertThat(sanitize("log-sensitive-fields",
            "Account recovery {\"user\":\"u-42\", \"securityAnswer\" : \"Blue Whale\"}"))
        .isEqualTo("Account recovery {\"user\":\"u-42\", \"securityAnswer\" : \"**********\"}");
  }
}
