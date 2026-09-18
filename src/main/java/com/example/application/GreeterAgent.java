package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

/** Phrases a short greeting in a given language. Which language to use is decided elsewhere (see SessionLanguageEntity). */
@Component(id = "greeter-agent")
public class GreeterAgent extends Agent {

  private static final String SYSTEM_MESSAGE = """
      You are a greeter. The user message names a language.
      Reply with ONLY a short, natural greeting in that language - nothing else.
      Do not translate the instruction, do not explain your answer, do not add
      follow-up questions or extra pleasantries. Output only the greeting itself.
      """.stripIndent();

  public Effect<String> greet(String languageDisplayName) {
    return effects()
        .systemMessage(SYSTEM_MESSAGE)
        .userMessage("Language: " + languageDisplayName)
        .thenReply();
  }
}
