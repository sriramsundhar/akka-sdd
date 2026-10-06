package com.rag.application;

import com.mongodb.client.MongoClient;
import com.typesafe.config.Config;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.store.embedding.mongodb.MongoDbEmbeddingStore;

public class Knowledge {

  private final RetrievalAugmentor retrievalAugmentor;
  private final ContentInjector contentInjector = new DefaultContentInjector();
  private static final String GEMINI_PATH = "akka.javasdk.agent.gemini";

  public Knowledge(MongoClient mongoClient, Config config) {
    var embeddingStore = MongoDbEmbeddingStore.builder()
        .fromClient(mongoClient)
        .databaseName("akka-docs")
        .collectionName("embeddings")
        .indexName("default")
        .createIndex(true)
        .build();
    var embeddingModel = GoogleAiEmbeddingModel.builder()
        .apiKey(config.getString(GEMINI_PATH + ".api-key"))
        .modelName(config.getString(GEMINI_PATH + ".model-name"))
        .outputDimensionality(1536)
        .build();

    var contentRetriever = EmbeddingStoreContentRetriever.builder()
        .embeddingStore(embeddingStore)
        .embeddingModel(embeddingModel)
        .maxResults(10)
        .minScore(0.1)
        .build();

    this.retrievalAugmentor = DefaultRetrievalAugmentor.builder()
        .contentRetriever(contentRetriever)
        .build();
  }

  public String addKnowledge(String question) {
    var chatMessage = new UserMessage(question);
    var metadata = Metadata.from(chatMessage, null, null);
    var augmentationRequest = new AugmentationRequest(chatMessage, metadata);

    var result = retrievalAugmentor.augment(augmentationRequest);
    UserMessage augmented = (UserMessage) contentInjector.inject(
        result.contents(),
        chatMessage);
    return augmented.singleText();
  }
}
