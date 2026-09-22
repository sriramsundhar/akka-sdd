package com.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;

@Component(id = "weather-agent")
public class WeatherAgent extends Agent {

  private static final Logger logger = LoggerFactory.getLogger(WeatherAgent.class);

  private static final String SYSTEM_MESSAGE = """
      You are a weather agent.
      Your job is to provide weather information.
      You provide current weather, forecasts, and other related information.

      The responses from the weather services are in json format. You need to digest
      it into human language. Be aware that Celsius temperature is in temp_c field.
      Fahrenheit temperature is in temp_f field.
      """.stripIndent();

  public Effect<String> query(String message) {
    logger.info("Invoked with: {}", message);
    return effects()
        .model(ModelProvider.fromConfig("litellm"))
        .systemMessage(SYSTEM_MESSAGE).userMessage(message).thenReply();
  }

  @FunctionTool(description = "Returns the current weather forecast for a given city.")
  private String getCurrentWeather(
      @Description("A location or city name.") String location) {

    return "its sunny and plesent at this time.";
  }
}
