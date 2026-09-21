package com.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.Component;

@Component(id = "activity-agent")
public class ActivityAgent extends Agent {

  private static final Logger logger = LoggerFactory.getLogger(ActivityAgent.class);

  private static final String SYSTEM_MESAGE = """
      You are an activity agent. Your job is to suggest activities in the
        real world. Like for example, a team building activity, sports, an
        indoor or outdoor game, board games, a city trip, etc.
      """.stripIndent();

  public Effect<String> suggestActivity(String message) {
    logger.info("invoeke with message: {}", message);
    return effects().model(ModelProvider.fromConfig("litellm"))
        .systemMessage(SYSTEM_MESAGE)
        .userMessage(message)
        .thenReply();
  }
}
