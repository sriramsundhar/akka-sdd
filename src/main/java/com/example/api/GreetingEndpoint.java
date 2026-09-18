package com.example.api;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpException;
import com.example.application.GreeterAgent;
import com.example.application.SessionLanguageEntity;
import com.example.domain.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@HttpEndpoint("/greetings")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class GreetingEndpoint {

  private static final Logger logger = LoggerFactory.getLogger(GreetingEndpoint.class);

  public record TurnRequest(String message) {}

  public record TurnResponse(String language, String greeting) {}

  private final ComponentClient componentClient;

  public GreetingEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/{sessionId}")
  public TurnResponse turn(String sessionId, TurnRequest request) {
    if (request.message() == null || request.message().isBlank()) {
      throw HttpException.badRequest("message must not be blank");
    }

    Language language = componentClient
        .forKeyValueEntity(sessionId)
        .method(SessionLanguageEntity::selectNextLanguage)
        .invoke();
    logger.info("session={} selected language={}", sessionId, language.code());

    String greeting;
    try {
      greeting = componentClient
          .forAgent()
          .inSession(sessionId)
          .method(GreeterAgent::greet)
          .invoke(language.displayName());
    } catch (RuntimeException e) {
      logger.warn("session={} greeting model call failed: {}", sessionId, e.getMessage());
      throw HttpException.error(StatusCodes.BAD_GATEWAY, "greeting model call failed: " + e.getMessage());
    }

    logger.info("session={} turn complete language={}", sessionId, language.code());
    return new TurnResponse(language.displayName(), greeting);
  }
}
