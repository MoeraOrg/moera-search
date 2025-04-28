package org.moera.search.scanner.updates;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.search.job.Job;
import org.moera.search.scanner.NodeIngest;

public class SubscriptionJob extends Job<SubscriptionJob.Parameters, Object> {

    public static class Parameters {

        private boolean unsubscribe;
        private String nodeName;
        private String subscriptionName;
        private String feedName;

        public Parameters() {
        }

        public Parameters(boolean unsubscribe, String nodeName, String subscriptionName, String feedName) {
            this.unsubscribe = unsubscribe;
            this.nodeName = nodeName;
            this.subscriptionName = subscriptionName;
            this.feedName = feedName;
        }

        public boolean isUnsubscribe() {
            return unsubscribe;
        }

        public void setUnsubscribe(boolean unsubscribe) {
            this.unsubscribe = unsubscribe;
        }

        public String getNodeName() {
            return nodeName;
        }

        public void setNodeName(String nodeName) {
            this.nodeName = nodeName;
        }

        public String getSubscriptionName() {
            return subscriptionName;
        }

        public void setSubscriptionName(String subscriptionName) {
            this.subscriptionName = subscriptionName;
        }

        public String getFeedName() {
            return feedName;
        }

        public void setFeedName(String feedName) {
            this.feedName = feedName;
        }

    }

    @Inject
    private NodeIngest nodeIngest;

    public SubscriptionJob() {
        noRetry();
    }

    @Override
    protected void setParameters(String parameters, ObjectMapper objectMapper) throws JsonProcessingException {
        this.parameters = objectMapper.readValue(parameters, SubscriptionJob.Parameters.class);
    }

    @Override
    protected void setState(String state, ObjectMapper objectMapper) throws JsonProcessingException {
        this.state = null;
    }

    @Override
    protected void execute() throws Exception {
        if (!parameters.unsubscribe) {
            nodeIngest.subscribed(parameters.nodeName, parameters.subscriptionName, parameters.feedName);
        } else {
            nodeIngest.unsubscribed(parameters.nodeName, parameters.subscriptionName, parameters.feedName);
        }
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " for " + parameters.nodeName
            + (!parameters.unsubscribe ? " subscribes to " : " unsubscribes from ") + parameters.subscriptionName
            + " feed " + parameters.feedName;
    }

}
