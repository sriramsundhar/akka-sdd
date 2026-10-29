package com.example.api;

import java.util.List;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.HttpResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes synthetic log lines containing sensitive values, so the log-only sanitizers configured in
 * application.conf can be seen masking them. The response never contains the sample values.
 */
@HttpEndpoint("/log-sanitization-demo")
// Demo only: open to all callers so the sample can be triggered with curl. Do not copy this ACL to
// real endpoints.
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class LogSanitizationDemoEndpoint {

  private static final Logger logger = LoggerFactory.getLogger(LogSanitizationDemoEndpoint.class);

  // Synthetic data only: example.com address, fictional 555-01xx number, Visa test card,
  // documentation IBAN, and RFC 5737 / RFC 3849 documentation IP addresses.
  private static final String EMAIL = "Password reset requested for jane.doe@example.com";
  private static final String PHONE = "SMS verification sent to 202-555-0143";
  private static final String CREDIT_CARD = "Payment declined for card 4111 1111 1111 1111";
  private static final String IBAN = "Refund issued to GB82WEST12345698765432";
  private static final String IP_ADDRESS = "Login from 192.0.2.10 and 2001:db8:0:0:0:0:0:1";
  private static final String CUSTOM =
      "User update {\"user\":\"u-42\",\"ssn\":\"123-45-6789\",\"dateOfBirth\":\"1990-01-31\"} password=hunter2";
  private static final String SECURITY_ANSWER =
      "Account recovery {\"user\":\"u-42\", \"securityAnswer\" : \"Blue Whale\"}";

  @Get("/{kind}")
  public HttpResponse logSample(String kind) {
    List<String> lines = switch (kind) {
      case "email" -> List.of(EMAIL);
      case "phone" -> List.of(PHONE);
      case "credit-card" -> List.of(CREDIT_CARD);
      case "iban" -> List.of(IBAN);
      case "ip-address" -> List.of(IP_ADDRESS);
      case "custom" -> List.of(CUSTOM, SECURITY_ANSWER);
      case "all" -> List.of(EMAIL, PHONE, CREDIT_CARD, IBAN, IP_ADDRESS, CUSTOM, SECURITY_ANSWER);
      default -> List.of();
    };
    if (lines.isEmpty()) {
      return HttpResponses.badRequest(
          "Unknown kind. Use one of: email, phone, credit-card, iban, ip-address, custom, all");
    }

    lines.forEach(logger::info);
    return HttpResponses.ok(
        "Logged " + lines.size() + " sample line(s) for '" + kind + "'. Check the service logs.");
  }
}
