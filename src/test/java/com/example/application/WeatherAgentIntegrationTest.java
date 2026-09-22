package com.example.application;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;

public class WeatherAgentIntegrationTest extends TestKitSupport {

  private final TestModelProvider weatherModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withModelProvider(WeatherAgent.class, weatherModel);
  }

  @Test
  public void testAgent() {
    weatherModel.fixedResponse("It is sunny and 22°C in Madrid.");

    var sessionId = UUID.randomUUID().toString();
    var message = "I am in Madrid";
    var forecast = componentClient
        .forAgent()
        .inSession(sessionId)
        .method(WeatherAgent::query)
        .invoke(message);

    assertThat(forecast).isNotBlank();
  }
}
