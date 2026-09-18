package com.example.application;

import akka.javasdk.testkit.TestKitSupport;
import com.example.domain.Language;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class GreeterConcurrencyTest extends TestKitSupport {

  @Test
  void concurrentTurnsForTheSameSessionNeverSelectADuplicateLanguage() throws Exception {
    String sessionId = "concurrency-test-session";
    int turns = 10;
    ExecutorService pool = Executors.newFixedThreadPool(turns);
    try {
      List<Future<Language>> futures = IntStream.range(0, turns)
          .mapToObj(i -> pool.submit(() -> componentClient
              .forKeyValueEntity(sessionId)
              .method(SessionLanguageEntity::selectNextLanguage)
              .invoke()))
          .toList();

      List<String> codes = new ArrayList<>();
      for (Future<Language> future : futures) {
        codes.add(future.get(10, TimeUnit.SECONDS).code());
      }

      assertThat(codes).hasSize(turns);
      assertThat(codes).doesNotHaveDuplicates();
    } finally {
      pool.shutdownNow();
    }
  }
}
