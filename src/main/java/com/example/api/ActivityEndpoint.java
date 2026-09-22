package com.example.api;

import java.util.UUID;

import com.example.application.ActivityCoordinator;
import com.example.application.AgentTeamWorkflow;
import com.example.application.PreferencesEntity;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;

// Opened up for access from the public internet to make the service easy to try out.
// For actual services meant for production this must be carefully considered,
// and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint
public class ActivityEndpoint {

  public record Request(String message) {
  }

  private final ComponentClient componentClient;

  public ActivityEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/activities/{userId}")
  public HttpResponse suggestActivities(String userId, Request request) {
    var sessionId = UUID.randomUUID().toString();

    var res = componentClient
        .forWorkflow(sessionId)
        .method(AgentTeamWorkflow::start)
        .invoke(new AgentTeamWorkflow.Request(userId, request.message()));
    return HttpResponses.created(res, "activities/" + userId + "/workflow/" + sessionId);
  }

  @Post("/activities/autonomous/{userId}")
  public HttpResponse suggestActivitiesAutonomously(String userId, Request request) {
    var sessionId = UUID.randomUUID().toString();
    var instructions = "User: " + userId + "\n\n" + request.message();

    var taskId = componentClient.forAutonomousAgent(ActivityCoordinator.class, sessionId)
        .runSingleTask(ActivityCoordinator.SUGGEST_ACTIVITIES.instructions(instructions));
    return HttpResponses.created(taskId, "activities/" + userId + "/autonomous/" + taskId);
  }

  @Get("/activities/{userId}/workflow/{taskId}")
  public HttpResponse suggestedAcctivities(String userId, String taskId) {
    var res = componentClient.forWorkflow(taskId)
        .method(AgentTeamWorkflow::getAnswer)
        .invoke();

    if (res.isEmpty()) {
      return HttpResponses.notFound(
          "Answer for '" + taskId + "' not available (yet)");
    } else {
      return HttpResponses.ok(res);
    }
  }

  @Get("/activities/${userId}/autonomous/${taskId}")
  public HttpResponse suggestActivitiesAutonomusly(String userId, String taskId) {
    var snapshot = componentClient.forTask(taskId).get(ActivityCoordinator.SUGGEST_ACTIVITIES);

    return snapshot.result()
        .<HttpResponse>map(HttpResponses::ok)
        .orElseGet(
            () -> HttpResponses.notFound("Answer for '" + taskId + "' not available (yet)"));
  }

  public record AddPreference(String preference) {
  }

  @Post("/preferences/{userId}")
  public HttpResponse addPreference(String userId, AddPreference request) {
    componentClient
        .forEventSourcedEntity(userId)
        .method(PreferencesEntity::addPreference)
        .invoke(new PreferencesEntity.AddPreference(request.preference()));

    return HttpResponses.created();
  }

  @Get("/preferences/{userId}")
  public HttpResponse getPreferences(String userId) {
    var preferences = componentClient.forEventSourcedEntity(userId)
        .method(PreferencesEntity::getPreferences)
        .invoke();
    return HttpResponses.ok(preferences);
  }
}
