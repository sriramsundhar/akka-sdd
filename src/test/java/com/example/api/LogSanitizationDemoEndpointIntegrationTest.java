package com.example.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.http.StrictResponse;
import akka.javasdk.testkit.TestKitSupport;
import akka.util.ByteString;

public class LogSanitizationDemoEndpointIntegrationTest extends TestKitSupport {

  private static final List<String> RAW_SAMPLE_VALUES = List.of(
      "jane.doe@example.com",
      "202-555-0143",
      "4111 1111 1111 1111",
      "GB82WEST12345698765432",
      "192.0.2.10",
      "2001:db8:0:0:0:0:0:1",
      "123-45-6789",
      "1990-01-31",
      "hunter2",
      "Blue Whale");

  private StrictResponse<ByteString> get(String kind) {
    return httpClient.GET("/log-sanitization-demo/" + kind).invoke();
  }

  @Test
  public void everyKnownKindIsConfirmedWithoutEchoingSampleValues() {
    for (var kind : List.of("email", "phone", "credit-card", "iban", "ip-address", "custom", "all")) {
      var response = get(kind);
      var body = response.body().utf8String();

      assertThat(response.status()).as(kind).isEqualTo(StatusCodes.OK);
      assertThat(body).as(kind).contains("'" + kind + "'");
      assertThat(RAW_SAMPLE_VALUES).as(kind).noneMatch(body::contains);
    }
  }

  @Test
  public void singleKindLogsOneLine() {
    assertThat(get("email").body().utf8String()).startsWith("Logged 1 sample line(s)");
  }

  @Test
  public void customKindLogsBothSensitiveFieldLines() {
    assertThat(get("custom").body().utf8String()).startsWith("Logged 2 sample line(s)");
  }

  @Test
  public void allKindLogsEverySampleLine() {
    assertThat(get("all").body().utf8String()).startsWith("Logged 7 sample line(s)");
  }

  @Test
  public void unknownKindIsRejectedAsBadRequest() {
    var response = get("unknown");

    assertThat(response.status()).isEqualTo(StatusCodes.BAD_REQUEST);
    assertThat(response.body().utf8String())
        .startsWith("Unknown kind. Use one of:")
        .doesNotContain("unknown");
  }
}
