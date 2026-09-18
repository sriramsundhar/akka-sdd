package com.example.application;

import akka.javasdk.testkit.KeyValueEntityTestKit;
import com.example.domain.Language;
import com.example.domain.LanguagePool;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GreeterRotationTest {

  @Test
  void noLanguageRepeatsWithinASessionUntilEveryPoolLanguageHasBeenUsed() {
    var testKit = KeyValueEntityTestKit.of(SessionLanguageEntity::new);
    Set<String> seen = new HashSet<>();

    for (int i = 0; i < LanguagePool.ALL.size(); i++) {
      var result = testKit.method(SessionLanguageEntity::selectNextLanguage).invoke();
      assertThat(result.isReply()).isTrue();

      Language language = result.getReply();
      assertThat(seen).doesNotContain(language.code());
      seen.add(language.code());
    }

    assertThat(seen).hasSize(LanguagePool.ALL.size());
  }
}
