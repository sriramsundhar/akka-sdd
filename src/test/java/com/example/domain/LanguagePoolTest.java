package com.example.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class LanguagePoolTest {

  @Test
  void firstTurnPicksFromTheFullPoolWhenNoLanguageHasBeenUsedYet() {
    var result = LanguagePool.selectNext(List.of(), new Random(1));

    assertThat(LanguagePool.ALL).extracting(Language::code).contains(result.language().code());
    assertThat(result.updatedUsedCodes()).containsExactly(result.language().code());
  }

  @Test
  void subsequentTurnExcludesAlreadyUsedLanguagesWhilePoolIsNotExhausted() {
    List<String> used = List.of(LanguagePool.ALL.get(0).code(), LanguagePool.ALL.get(1).code());

    for (int seed = 0; seed < 100; seed++) {
      var result = LanguagePool.selectNext(used, new Random(seed));
      assertThat(used).doesNotContain(result.language().code());
    }
  }

  @Test
  void updatedHistoryNeverGrowsPastPoolSizeOrContainsDuplicates() {
    List<String> used = new ArrayList<>();
    Random random = new Random(42);

    for (int i = 0; i < LanguagePool.ALL.size() * 3; i++) {
      var result = LanguagePool.selectNext(used, random);
      used = new ArrayList<>(result.updatedUsedCodes());
      assertThat(used).doesNotHaveDuplicates();
      assertThat(used.size()).isLessThanOrEqualTo(LanguagePool.ALL.size());
    }
  }

  @Test
  void onceEveryLanguageIsUsedTheLeastRecentlyUsedIsSelectedNext() {
    List<String> fullHistory = LanguagePool.ALL.stream().map(Language::code).toList();

    var result = LanguagePool.selectNext(fullHistory, new Random());

    assertThat(result.language().code()).isEqualTo(fullHistory.get(0));
    List<String> updated = result.updatedUsedCodes();
    assertThat(updated).hasSize(fullHistory.size());
    assertThat(updated.get(updated.size() - 1)).isEqualTo(fullHistory.get(0));
    assertThat(updated.get(0)).isEqualTo(fullHistory.get(1));
  }
}
