package org.moera.search.scanner;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.types.Scope;
import org.moera.lib.node.types.SubscriberDescription;
import org.moera.lib.node.types.SubscriptionType;
import org.moera.search.api.NodeApi;
import org.moera.search.data.NodeRepository;
import org.moera.search.job.Job;

public class SubscribeJob extends Job<SubscribeJob.Parameters, SubscribeJob.State> {

    public static class Parameters {

        private String nodeName;

        public Parameters() {
        }

        public Parameters(String nodeName) {
            this.nodeName = nodeName;
        }

        public String getNodeName() {
            return nodeName;
        }

        public void setNodeName(String nodeName) {
            this.nodeName = nodeName;
        }

    }

    public static class State {

        private String subscriberId;

        public State() {
        }

        public String getSubscriberId() {
            return subscriberId;
        }

        public void setSubscriberId(String subscriberId) {
            this.subscriberId = subscriberId;
        }

    }

    @Inject
    private NodeApi nodeApi;

    @Inject
    private NodeRepository nodeRepository;

    public SubscribeJob() {
        state = new State();
        retryCount(3, "PT5M");
    }

    @Override
    protected void setParameters(String parameters, ObjectMapper objectMapper) throws JsonProcessingException {
        this.parameters = objectMapper.readValue(parameters, Parameters.class);
    }

    @Override
    protected void setState(String state, ObjectMapper objectMapper) throws JsonProcessingException {
        this.state = objectMapper.readValue(state, State.class);
    }

    @Override
    protected void execute() throws Exception {
        if (state.subscriberId == null) {
            var description = new SubscriberDescription();
            description.setType(SubscriptionType.SEARCH);
            state.subscriberId = nodeApi
                .at(parameters.nodeName, generateCarte(parameters.nodeName, Scope.SUBSCRIBE))
                .createSubscriber(description).getId();
            checkpoint();
        }

        database.executeWriteWithoutResult(() -> nodeRepository.subscribed(parameters.nodeName, state.subscriberId));
    }

    @Override
    protected void failed() {
        super.failed();
        database.executeWriteWithoutResult(() -> nodeRepository.subscribeFailed(parameters.nodeName));
    }

}
