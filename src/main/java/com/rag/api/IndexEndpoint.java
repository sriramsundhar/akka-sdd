package com.rag.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import com.rag.application.RagIndexingWorkflow;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET)) @HttpEndpoint("/api/index")
public class IndexEndpoint {

    // private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ComponentClient componentClient;

    public IndexEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // @Post("/start")
    public HttpResponse startIndexation() {
        componentClient.forWorkflow("indexing").method(RagIndexingWorkflow::start).invoke();
        return HttpResponses.accepted();
    }

    // @Post("/abort")
    public HttpResponse abortIndexation() {
        componentClient.forWorkflow("indexing").method(RagIndexingWorkflow::abort).invoke();
        return HttpResponses.accepted();
    }
}
