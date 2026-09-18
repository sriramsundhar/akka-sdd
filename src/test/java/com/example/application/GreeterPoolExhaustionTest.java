package com.example.application;

import akka.javasdk.testkit.TestKitSupport;
import com.example.domain.Language;
import com.example.domain.LanguagePool;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GreeterPoolExhaustionTest extends TestKitSupport {

  @Test
  void afterExhaustingThePoolTheSessionRestartsLeastRecentlyUsedFirst() {
    String sessionId = "exhaustion-test-session";
    List<String> seen = new ArrayList<>();

    for (int i = 0; i < LanguagePool.ALL.size(); i++) {
      seen.add(selectNext(sessionId).code());
    }
    assertThat(seen).doesNotHaveDuplicates();
    assertThat(seen).hasSize(LanguagePool.ALL.size());

    // Pool is now exhausted; the next turn must not fail, and must restart with the
    // least-recently-used language (the first one this session ever used).
    Language afterExhaustion = selectNext(sessionId);
    assertThat(afterExhaustion.code()).isEqualTo(seen.get(0));

    // The turn after that continues the restart with the next least-recently-used language.
    Language afterExhaustion2 = selectNext(sessionId);
    assertThat(afterExhaustion2.code()).isEqualTo(seen.get(1));
  }

  private Language selectNext(String sessionId) {
    return componentClient
        .forKeyValueEntity(sessionId)
        .method(SessionLanguageEntity::selectNextLanguage)
        .invoke();
  }
}
