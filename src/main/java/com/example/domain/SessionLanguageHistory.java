package com.example.domain;

import java.util.List;

/** A session's rotation state: the languages already used, ordered oldest-used first. */
public record SessionLanguageHistory(List<String> usedLanguageCodes) {

  public static SessionLanguageHistory empty() {
    return new SessionLanguageHistory(List.of());
  }
}
