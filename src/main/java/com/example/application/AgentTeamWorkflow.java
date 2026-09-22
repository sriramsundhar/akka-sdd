package com.example.application;

import static java.time.Duration.ofSeconds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.StepName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;

@Component(id = "agent-team")
public class AgentTeamWorkflow extends Workflow<AgentTeamWorkflow.State> {

  private static final Logger logger = LoggerFactory.getLogger(AgentTeamWorkflow.class);

  public record Request(String userId, String message) {
  }

  public record State(String userId, String userQuery, String finalAnswer) {
    State withAnswer(String a) {
      return new State(userId, userQuery, a);
    }
  }

  private final ComponentClient componentClient;

  public AgentTeamWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<Done> start(Request request) {
    return effects()
        .updateState(new State(request.userId(), request.message(), ""))
        .transitionTo(AgentTeamWorkflow::askWeather)
        .thenReply(Done.getInstance());
  }

  public Effect<String> getAnswer() {
    if (currentState() == null || currentState().finalAnswer.isEmpty()) {
      String workflowId = commandContext().workflowId();
      return effects()
          .error("Workflow '" + workflowId + "' not started, or not completed");
    } else {
      return effects().reply(currentState().finalAnswer);
    }
  }

  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder()
        .stepTimeout(AgentTeamWorkflow::askWeather, ofSeconds(60))
        .stepTimeout(AgentTeamWorkflow::suggestActivities, ofSeconds(60))
        .defaultStepRecovery(RecoverStrategy.maxRetries(2).failoverTo(AgentTeamWorkflow::error))
        .build();
  }

  @StepName("weather")
  private StepEffect askWeather() {
    var forecast = componentClient
        .forAgent()
        .inSession(sessionId())
        .method(WeatherAgent::query)
        .invoke(currentState().userQuery);

    logger.info("Weather forecast: {}", forecast);

    return stepEffects()
        .thenTransitionTo(AgentTeamWorkflow::suggestActivities);
  }

  @StepName("activities")
  private StepEffect suggestActivities() {
    var suggestion = componentClient
        .forAgent()
        .inSession(sessionId())
        .method(ActivityAgent::suggestActivity)
        .invoke(new ActivityAgent.Request(currentState().userId(), currentState().userQuery()));

    logger.info("Activities: {}", suggestion);

    return stepEffects()
        .updateState(currentState().withAnswer(suggestion))
        .thenEnd();
  }

  private StepEffect error() {
    return stepEffects().thenEnd();
  }

  private String sessionId() {
    // the workflow corresponds to the session
    return commandContext().workflowId();
  }
}
