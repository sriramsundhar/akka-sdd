package com.rag.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.Component;

@Component(id = "ask-akka-agent", name = "Ask Akka", description = "Expert in Akka")
public class AskAkkaAgent extends Agent {

  private static final String SYSTEM_MESSAGE = """
      You are a very enthusiastic Akka representative who loves to help people!
      Given the following sections from the Akka SDK documentation, answer the question
      using only that information, outputted in markdown format.
      If you are unsure and the text is not explicitly written in the documentation, say:
      Sorry, I don't know how to help with that.
      """.stripIndent();

  public StreamEffect ask(String question) {
    return streamEffects().model(ModelProvider.fromConfig("vertex-ai"))
        .systemMessage(SYSTEM_MESSAGE)
        .userMessage(question)
        .thenReply();
  }
}
