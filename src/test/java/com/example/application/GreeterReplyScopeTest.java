package com.example.application;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GreeterReplyScopeTest extends TestKitSupport {

  private final TestModelProvider greeterModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withModelProvider(GreeterAgent.class, greeterModel);
  }

  @Test
  void replyIsExactlyWhatTheModelReturnsForTheRequestedLanguage() {
    greeterModel.fixedResponse("Bonjour !");

    String reply = componentClient
        .forAgent()
        .inSession("reply-scope-test-session-1")
        .method(GreeterAgent::greet)
        .invoke("French");

    assertThat(reply).isEqualTo("Bonjour !");
  }

  @Test
  void requestSentToTheModelNamesTheRequestedLanguage() {
    greeterModel.whenMessage(msg -> msg.contains("Japanese")).reply("こんにちは！");

    String reply = componentClient
        .forAgent()
        .inSession("reply-scope-test-session-2")
        .method(GreeterAgent::greet)
        .invoke("Japanese");

    assertThat(reply).isEqualTo("こんにちは！");
  }
}
