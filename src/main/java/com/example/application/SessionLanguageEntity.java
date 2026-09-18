package com.example.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.domain.Language;
import com.example.domain.LanguagePool;
import com.example.domain.SessionLanguageHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks, per session, which languages have already been used and selects the next one.
 *
 * <p>Each Key Value Entity instance handles one command at a time, so concurrent turns for the
 * same session are naturally serialized: two overlapping requests can never both select the same
 * "unused" language or corrupt the recorded history.
 */
@Component(id = "session-language")
public class SessionLanguageEntity extends KeyValueEntity<SessionLanguageHistory> {

  private static final Logger logger = LoggerFactory.getLogger(SessionLanguageEntity.class);

  @Override
  public SessionLanguageHistory emptyState() {
    return SessionLanguageHistory.empty();
  }

  public Effect<Language> selectNextLanguage() {
    LanguagePool.SelectionResult result = LanguagePool.selectNext(currentState().usedLanguageCodes());
    logger.debug(
        "entityId={} usedBefore={} selected={} usedAfter={}",
        commandContext().entityId(),
        currentState().usedLanguageCodes(),
        result.language().code(),
        result.updatedUsedCodes());
    return effects()
        .updateState(new SessionLanguageHistory(result.updatedUsedCodes()))
        .thenReply(result.language());
  }
}
