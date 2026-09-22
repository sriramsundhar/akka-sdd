package com.example.application;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.TestKitSupport;

public class WeatherAgentIntegrationTest extends TestKitSupport {

  @Test
  public void testAgent() {
    var sessionId = UUID.randomUUID().toString();
    var message = "I am in Madrid";
    var forecast = componentClient
        .forAgent()
        .inSession(sessionId)
        .method(WeatherAgent::query)
        .invoke(message);

    System.out.println(forecast);
    assertThat(forecast).isNotBlank();
  }
}
