package com.example.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The fixed catalog of languages the greeter rotates through, plus the rotation algorithm.
 *
 * <p>Rotation rule: pick uniformly at random among languages not yet in the session's used-list;
 * once every language has been used, pick the least-recently-used one (the front of the list).
 * Moving a code to the back of the list on every selection (removing any prior occurrence first)
 * is what makes "least-recently-used" simply "the front of the list".
 */
public final class LanguagePool {

  public static final List<Language> ALL = List.of(
      new Language("en", "English"),
      new Language("fr", "French"),
      new Language("es", "Spanish"),
      new Language("de", "German"),
      new Language("it", "Italian"),
      new Language("pt", "Portuguese"),
      new Language("nl", "Dutch"),
      new Language("sv", "Swedish"),
      new Language("da", "Danish"),
      new Language("no", "Norwegian"),
      new Language("fi", "Finnish"),
      new Language("pl", "Polish"),
      new Language("cs", "Czech"),
      new Language("sk", "Slovak"),
      new Language("hu", "Hungarian"),
      new Language("ro", "Romanian"),
      new Language("bg", "Bulgarian"),
      new Language("el", "Greek"),
      new Language("tr", "Turkish"),
      new Language("ru", "Russian"),
      new Language("uk", "Ukrainian"),
      new Language("he", "Hebrew"),
      new Language("ar", "Arabic"),
      new Language("fa", "Persian"),
      new Language("ur", "Urdu"),
      new Language("hi", "Hindi"),
      new Language("bn", "Bengali"),
      new Language("pa", "Punjabi"),
      new Language("ta", "Tamil"),
      new Language("te", "Telugu"),
      new Language("mr", "Marathi"),
      new Language("gu", "Gujarati"),
      new Language("th", "Thai"),
      new Language("vi", "Vietnamese"),
      new Language("id", "Indonesian"),
      new Language("ms", "Malay"),
      new Language("tl", "Filipino"),
      new Language("zh", "Chinese"),
      new Language("ja", "Japanese"),
      new Language("ko", "Korean"),
      new Language("sw", "Swahili"),
      new Language("am", "Amharic"),
      new Language("yo", "Yoruba"),
      new Language("ha", "Hausa"),
      new Language("zu", "Zulu"),
      new Language("af", "Afrikaans"),
      new Language("is", "Icelandic"),
      new Language("ga", "Irish"),
      new Language("cy", "Welsh"),
      new Language("ca", "Catalan"));

  private LanguagePool() {}

  /** The result of a rotation step: the language chosen, and the session's updated used-language history. */
  public record SelectionResult(Language language, List<String> updatedUsedCodes) {}

  public static SelectionResult selectNext(List<String> usedCodesOldestFirst) {
    return selectNext(usedCodesOldestFirst, new Random());
  }

  public static SelectionResult selectNext(List<String> usedCodesOldestFirst, Random random) {
    List<Language> unused = new ArrayList<>();
    for (Language language : ALL) {
      if (!usedCodesOldestFirst.contains(language.code())) {
        unused.add(language);
      }
    }

    Language selected;
    if (!unused.isEmpty()) {
      selected = unused.get(random.nextInt(unused.size()));
    } else {
      // Pool exhausted: the least-recently-used language is the one at the front of the history.
      String leastRecentlyUsedCode = usedCodesOldestFirst.get(0);
      selected = byCode(leastRecentlyUsedCode);
    }

    List<String> updated = new ArrayList<>(usedCodesOldestFirst);
    updated.remove(selected.code());
    updated.add(selected.code());
    return new SelectionResult(selected, updated);
  }

  private static Language byCode(String code) {
    for (Language language : ALL) {
      if (language.code().equals(code)) {
        return language;
      }
    }
    throw new IllegalArgumentException("Unknown language code: " + code);
  }
}
