package com.example.application;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import dev.langchain4j.agent.tool.P;

@Component(id = "activity-agent")
public class ActivityAgent extends Agent {

  private static final Logger logger = LoggerFactory.getLogger(ActivityAgent.class);

  private final ComponentClient componentClient;

  public record Request(String userId, String message) {
  }

  private static final String SYSTEM_MESAGE = """
      You are an activity agent. Your job is to suggest activities in the
        real world. Like for example, a team building activity, sports, an
        indoor or outdoor game, board games, a city trip, etc.
      """.stripIndent();

  public ActivityAgent(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> suggestActivity(Request request) {

    var preferences = componentClient.forEventSourcedEntity(request.userId)
        .method(PreferencesEntity::getPreferences)
        .invoke();

    String userMessage;
    if (preferences.entries().isEmpty()) {
      userMessage = request.message;
    } else {
      userMessage = request.message() +
          "\nPreferences:\n" +
          preferences.entries().stream().collect(Collectors.joining("\n", "- ", ""));
    }

    logger.info("invoke with usserId:{} message: {} with preferences", request.userId, request.message, userMessage);

    return effects().model(ModelProvider.fromConfig("litellm"))
        .systemMessage(SYSTEM_MESAGE)
        .userMessage(userMessage)
        .thenReply();
  }
}
