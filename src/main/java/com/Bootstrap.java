package com;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.rag.application.Knowledge;
import com.typesafe.config.Config;

@Setup
public class Bootstrap implements ServiceSetup {

  private Config config;

  public Bootstrap(Config config) {
    this.config = config;
  }

  @Override
  public DependencyProvider createDependencyProvider() {
    MongoClient mongoClient = MongoClients.create(config.getString("mongodb.uri"));
    // Knowledge's constructor checks/creates a MongoDB Atlas Search index, which
    // requires a live MongoDB connection. Build it lazily - only when a component
    // that actually depends on it (AskAkkaAgent) is instantiated - rather than
    // unconditionally on every service boot, which would otherwise require MongoDB
    // to be reachable just to run unrelated components/tests.
    Object lock = new Object();
    Knowledge[] knowledgeHolder = new Knowledge[1];

    return new DependencyProvider() {
      @SuppressWarnings("unchecked")
      @Override
      public <T> T getDependency(Class<T> cls) {
        if (cls.equals(MongoClient.class)) {
          return (T) mongoClient;
        }
        if (cls.equals(Knowledge.class)) {
          synchronized (lock) {
            if (knowledgeHolder[0] == null) {
              knowledgeHolder[0] = new Knowledge(mongoClient, config);
            }
          }
          return (T) knowledgeHolder[0];
        }

        return null;
      }
    };
  }
}
