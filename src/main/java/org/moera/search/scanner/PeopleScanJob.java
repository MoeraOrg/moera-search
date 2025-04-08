package org.moera.search.scanner;

import java.util.Objects;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.exception.MoeraNodeApiAuthenticationException;
import org.moera.lib.node.types.Scope;
import org.moera.lib.node.types.SubscriptionType;
import org.moera.search.api.NodeApi;
import org.moera.search.data.NodeRepository;
import org.moera.search.job.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeopleScanJob extends Job<PeopleScanJob.Parameters, PeopleScanJob.State> {

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

        private boolean scannedFriends;
        private boolean scannedSubscriptions;

        public State() {
        }

        public boolean isScannedFriends() {
            return scannedFriends;
        }

        public void setScannedFriends(boolean scannedFriends) {
            this.scannedFriends = scannedFriends;
        }

        public boolean isScannedSubscriptions() {
            return scannedSubscriptions;
        }

        public void setScannedSubscriptions(boolean scannedSubscriptions) {
            this.scannedSubscriptions = scannedSubscriptions;
        }

    }

    private static final Logger log = LoggerFactory.getLogger(PeopleScanJob.class);

    @Inject
    private NodeApi nodeApi;

    @Inject
    private NodeRepository nodeRepository;

    public PeopleScanJob() {
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
        if (!state.scannedFriends) {
            try {
                var friends = nodeApi
                    .at(parameters.nodeName, generateCarte(parameters.nodeName, Scope.VIEW_PEOPLE))
                    .getFriends(null);
                for (var friend : friends) {
                    database.executeWriteWithoutResult(
                        () -> nodeRepository.addFriendship(parameters.nodeName, friend.getNodeName())
                    );
                }
            } catch (MoeraNodeApiAuthenticationException e) {
                log.info("Friend list is not public for {}, skipping", parameters.nodeName);
            }
            state.scannedFriends = true;
            checkpoint();
        }

        if (!state.scannedSubscriptions) {
            try {
                var subscriptions = nodeApi
                    .at(parameters.nodeName, generateCarte(parameters.nodeName, Scope.VIEW_PEOPLE))
                    .getSubscriptions(null, SubscriptionType.FEED);
                for (var subscription : subscriptions) {
                    if (!Objects.equals(subscription.getRemoteFeedName(), "timeline")) {
                        continue;
                    }
                    database.executeWriteWithoutResult(
                        () -> nodeRepository.addSubscription(parameters.nodeName, subscription.getRemoteNodeName())
                    );
                }
            } catch (MoeraNodeApiAuthenticationException e) {
                log.info("Subscriptions list is not public for {}, skipping", parameters.nodeName);
            }
            state.scannedSubscriptions = true;
            checkpoint();
        }

        database.executeWriteWithoutResult(() -> nodeRepository.scanPeopleSucceeded(parameters.nodeName));
    }

    @Override
    protected void failed() {
        super.failed();
        database.executeWriteWithoutResult(() -> nodeRepository.scanPeopleFailed(parameters.nodeName));
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " for " + parameters.nodeName;
    }

}
