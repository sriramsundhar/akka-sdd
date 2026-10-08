package com.rag.application;

//
// import static akka.Done.done;
// import static java.time.Duration.ofMinutes;
//
// import java.io.IOException;
// import java.io.InputStream;
// import java.net.JarURLConnection;
// import java.net.URI;
// import java.net.URL;
// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.nio.file.Paths;
// import java.util.ArrayList;
// import java.util.List;
// import java.util.Optional;
// import java.util.jar.JarFile;
// import java.util.stream.Stream;
//
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
//
// import com.mongodb.client.MongoClient;
// import com.typesafe.config.Config;
//
// import akka.Done;
// import akka.javasdk.annotations.Component;
// import akka.javasdk.annotations.StepName;
// import akka.javasdk.workflow.Workflow;
// import dev.langchain4j.data.document.BlankDocumentException;
// import dev.langchain4j.data.document.DefaultDocument;
// import dev.langchain4j.data.document.Document;
// import dev.langchain4j.data.document.Metadata;
// import dev.langchain4j.data.document.parser.TextDocumentParser;
// import dev.langchain4j.data.document.splitter.DocumentByCharacterSplitter;
// import dev.langchain4j.data.segment.TextSegment;
// import dev.langchain4j.model.embedding.EmbeddingModel;
// import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
// import dev.langchain4j.store.embedding.mongodb.MongoDbEmbeddingStore;
//
// /**
// * This workflow reads the files under src/main/resources/md-docs/ and create
// * the vector embeddings that are later
// * used to augment the LLM context.
// */
// // tag::shell[]
// @Component(id = "rag-indexing-workflow")
// public class RagIndexingWorkflow extends Workflow<RagIndexingWorkflow.State> {
//
// private static final String GEMINI_PATH = "akka.javasdk.agent.gemini";
// private final Logger logger = LoggerFactory.getLogger(getClass());
//
// // metadata key used to store file name
// private final String srcKey = "src";
//
// private DocumentByCharacterSplitter splitter;
//
// private MongoDbEmbeddingStore embeddingStore;
//
// private EmbeddingModel embeddingModel;
//
// public RagIndexingWorkflow(MongoClient mongoClient, Config config) {
// this.embeddingModel = GoogleAiEmbeddingModel.builder()
// .apiKey(config.getString(GEMINI_PATH + ".api-key"))
// .modelName(config.getString(GEMINI_PATH + ".model-name"))
// .outputDimensionality(1536)
// .build();
// this.embeddingStore = MongoDbEmbeddingStore.builder()
// .fromClient(mongoClient)
// .databaseName("akka-docs")
// .collectionName("embeddings")
// .indexName("default")
// .createIndex(true)
// .build();
//
// this.splitter = new DocumentByCharacterSplitter(500, 50); // <1>
// }
//
// public record State(List<String> toProcess, List<String> processed) { // <1>
// public static State of(List<String> toProcess) {
// return new State(toProcess, new ArrayList<>());
// }
//
// public Optional<String> head() {
// if (toProcess.isEmpty())
// return Optional.empty();
// else
// return Optional.of(toProcess.getFirst());
// }
//
// public State headProcessed() {
// if (!toProcess.isEmpty()) {
// processed.add(toProcess.removeFirst());
// }
// return new State(toProcess, processed);
// }
//
// /**
// * @return true if workflow has one or more documents to process, false
// * otherwise.
// */
// public boolean hasFilesToProcess() {
// return !toProcess.isEmpty();
// }
//
// public int totalFiles() {
// return processed.size() + toProcess.size();
// }
//
// public int totalProcessed() {
// return processed.size();
// }
// }
//
// @Override
// public State emptyState() {
// return State.of(new ArrayList<>());
// }
//
// @Override
// public WorkflowSettings settings() {
// return WorkflowSettings.builder()
// .defaultStepTimeout(ofMinutes(1))
// .build();
// }
//
// @StepName("processing-file")
// private StepEffect processingFileStep() {
// if (currentState().hasFilesToProcess()) {
// logger.info("Process file {}", currentState().head().get());
// // to process file
// indexFile(currentState().head().get());
// // update State
// var newState = currentState().headProcessed();
// logger.debug("Processed {} / {}", newState.totalProcessed(), newState.totalFiles());
// return stepEffects().updateState(newState)
// .thenTransitionTo(RagIndexingWorkflow::processingFileStep);
// } else {
// return stepEffects().thenPause();
// }
// }
//
// private void indexFile(String resourceName) {
// var fileName = resourceName.substring(resourceName.lastIndexOf('/') + 1);
// try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourceName)) {
// if (input == null) {
// logger.error("Resource not found: {}", resourceName);
// return;
// }
// Document doc = new TextDocumentParser().parse(input);
// var docWithMetadata = new DefaultDocument(doc.text(), Metadata.metadata(srcKey, fileName));
// var segments = splitter.split(docWithMetadata);
// logger.debug(
// "Created {} segments for document {}",
// segments.size(),
// fileName);
//
// segments.forEach(this::addSegment);
// } catch (BlankDocumentException e) {
// // some documents are blank, we need to skip them
// } catch (Exception e) {
// logger.error("Error reading file: {} - {}", resourceName, e.getMessage());
// }
// }
//
// private void addSegment(TextSegment seg) {
// var fileName = seg.metadata().getString(srcKey);
// var res = embeddingModel.embed(seg);
//
// logger.debug("Segment embedded. Source file '{}'.", fileName);
//
// embeddingStore.add(res.content(), seg);
// }
//
// public Effect<Done> start() {
// if (currentState().hasFilesToProcess()) {
// return effects().error("Workflow is currently processing documents");
// } else {
// List<String> documents;
// try {
// documents = listMdDocsResources();
// } catch (IOException e) {
// throw new RuntimeException(e);
// }
//
// return effects()
// .updateState(State.of(documents))
// .transitionTo(RagIndexingWorkflow::processingFileStep)
// .thenReply(done());
// }
// }
//
// /**
// * Lists the "md-docs" classpath resources ending in .md. Handles both an
// * exploded classpath (mvn exec:java, tests - "md-docs" resolves to a real
// * directory) and a packaged jar (standalone/Docker - "md-docs" resolves to
// * jar:file:...!/md-docs, which java.nio.file.Files cannot walk directly).
// */
// private List<String> listMdDocsResources() throws IOException {
// URL resource = getClass().getClassLoader().getResource("md-docs");
// if (resource == null) {
// return List.of();
// }
//
// if ("jar".equals(resource.getProtocol())) {
// JarURLConnection jarConnection = (JarURLConnection) resource.openConnection();
// // Avoid touching the JVM's shared/cached JarFile for the running jar -
// // open a private one we can safely close.
// jarConnection.setUseCaches(false);
// try (JarFile jarFile = jarConnection.getJarFile()) {
// return jarFile.stream()
// .map(entry -> entry.getName())
// .filter(name -> name.startsWith("md-docs/") && name.endsWith(".md"))
// .toList();
// }
// } else {
// var root = Paths.get(URI.create(resource.toString()));
// try (Stream<Path> paths = Files.walk(root)) {
// return paths
// .filter(Files::isRegularFile)
// .filter(path -> path.toString().endsWith(".md"))
// .map(path -> "md-docs/" + root.relativize(path).toString().replace(java.io.File.separatorChar,
// '/'))
// .toList();
// }
// }
// }
//
// public Effect<Done> abort() {
// logger.debug(
// "Aborting workflow. Current number of pending documents {}",
// currentState().toProcess.size());
// return effects().updateState(emptyState()).pause().thenReply(done());
// }
// }
