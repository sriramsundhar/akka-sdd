package com.example.application;

import akka.javasdk.agent.ModelProvider;
import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.Delegation;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.agent.task.Task;
import akka.javasdk.annotations.Component;

@Component(id = "activity-coordinator", description = """
    Coordinates worker agents to suggest real-world activities for a user. \
    Decides whether to consult the weather agent, the activity agent, or both, \
    and synthesizes their results into a single suggestion.\
    """)
public class ActivityCoordinator extends AutonomousAgent {
  public static final Task<String> SUGGEST_ACTIVITIES = Task.name(
      "SuggestActivities").description(
          """
              Suggest real-world activities for a user, taking weather and any stated preferences \
              into account. The task instructions begin with a "User: <userId>" line followed by \
              the user's question.\
              """);

  @Override
  public AgentDefinition definition() {
    return define().modelProvider(ModelProvider.fromConfig("vertex-ai"))
        .instructions("""
              When delegating to the activity agent, include the userId from the task header \
              (the "User: <userId>" line) in the request so the agent can fetch the user's \
              preferences.\
            """)
        .capability(TaskAcceptance.of(SUGGEST_ACTIVITIES).maxIterationsPerTask(3))
        .capability(Delegation.to(WeatherAgent.class, ActivityAgent.class));

  }

}
