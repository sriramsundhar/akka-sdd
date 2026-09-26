package com.example.application;

import akka.javasdk.agent.evaluator.ToxicityEvaluator;
import akka.javasdk.agent.task.TaskEntity;
import akka.javasdk.agent.task.TaskEvent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "evaluation-consumer")
@Consume.FromEventSourcedEntity(TaskEntity.class)
public class EvaluationConsumer extends Consumer {

  private static final Logger logger = LoggerFactory.getLogger(EvaluationConsumer.class);

  private final ComponentClient componentClient;

  public EvaluationConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(TaskEvent event) {
    if (event instanceof TaskEvent.TaskCompleted completed &&
        ActivityCoordinator.SUGGEST_ACTIVITIES.name().equals(completed.name())) {
      var taskId = completed.taskId();
      var snapshot = componentClient.forTask(taskId).get(ActivityCoordinator.SUGGEST_ACTIVITIES);

      // Custom LLM-as-judge: compare answer against the original (preference-aware)
      // request
      var judgement = componentClient
          .forAgent()
          .inSession(taskId)
          .method(EvaluatorAgent::evaluate)
          .invoke(
              new EvaluatorAgent.EvaluationRequest(
                  snapshot.instructions(),
                  snapshot.result().orElse("")));
      if (judgement.passed()) {
        logger.debug("LLM judge passed for task [{}]", taskId);
      } else {
        logger.warn(
            "LLM judge failed for task [{}], explanation: {}",
            taskId,
            judgement.explanation());
      }

      // Built-in toxicity evaluator on the final answer
      var toxicity = componentClient
          .forAgent()
          .inSession(taskId)
          .method(ToxicityEvaluator::evaluate)
          .invoke(snapshot.result().orElse(""));
      if (toxicity.passed()) {
        logger.debug("Toxicity check passed for task [{}]", taskId);
      } else {
        logger.warn(
            "Toxicity check failed for task [{}], explanation: {}",
            taskId,
            toxicity.explanation());
      }
    }
    return effects().done();
  }
}
