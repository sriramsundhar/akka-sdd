package com.example.application;

import akka.javasdk.testkit.TestKitSupport;
import com.example.domain.Language;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GreeterSessionIsolationTest extends TestKitSupport {

  @Test
  void twoSessionsRotateLanguagesIndependentlyOfEachOther() {
    String sessionA = "isolation-test-session-a";
    String sessionB = "isolation-test-session-b";

    List<String> codesA = new ArrayList<>();
    List<String> codesB = new ArrayList<>();

    for (int i = 0; i < 5; i++) {
      codesA.add(selectNext(sessionA).code());
      codesB.add(selectNext(sessionB).code());
    }

    // Each session's own history has no repeats, by itself.
    assertThat(codesA).doesNotHaveDuplicates();
    assertThat(codesB).doesNotHaveDuplicates();

    // A later turn in session A still only avoids session A's own history,
    // regardless of how many turns session B has had interleaved with it.
    Language next = selectNext(sessionA);
    assertThat(codesA).doesNotContain(next.code());
  }

  private Language selectNext(String sessionId) {
    return componentClient
        .forKeyValueEntity(sessionId)
        .method(SessionLanguageEntity::selectNextLanguage)
        .invoke();
  }
}
