package org.moera.search.scanner;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.types.Scope;
import org.moera.lib.node.types.SubscriberDescription;
import org.moera.lib.node.types.SubscriptionType;
import org.moera.lib.node.types.WhoAmI;
import org.moera.search.api.NodeApi;
import org.moera.search.data.NodeRepository;
import org.moera.search.job.Job;
import org.moera.search.media.MediaManager;
import org.moera.search.scanner.updates.PeopleScanUpdate;
import org.moera.search.scanner.updates.TimelineScanUpdate;

public class NameScanJob extends Job<NameScanJob.Parameters, NameScanJob.State> {

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

        private WhoAmI whoAmI;
        private boolean detailsUpdated;
        private boolean avatarDownloaded;
        private String subscriberId;

        public State() {
        }

        public WhoAmI getWhoAmI() {
            return whoAmI;
        }

        public void setWhoAmI(WhoAmI whoAmI) {
            this.whoAmI = whoAmI;
        }

        public boolean isDetailsUpdated() {
            return detailsUpdated;
        }

        public void setDetailsUpdated(boolean detailsUpdated) {
            this.detailsUpdated = detailsUpdated;
        }

        public boolean isAvatarDownloaded() {
            return avatarDownloaded;
        }

        public void setAvatarDownloaded(boolean avatarDownloaded) {
            this.avatarDownloaded = avatarDownloaded;
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

    @Inject
    private MediaManager mediaManager;

    @Inject
    private UpdateQueue updateQueue;

    public NameScanJob() {
        state = new State();
        retryCount(5, "PT5M");
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
        if (state.whoAmI == null) {
            state.whoAmI = nodeApi.at(parameters.nodeName).whoAmI();
            checkpoint();
        }

        if (!state.detailsUpdated) {
            database.writeNoResult(() -> nodeRepository.updateName(parameters.nodeName, state.whoAmI));
            state.detailsUpdated = true;
            checkpoint();
        }

        if (!state.avatarDownloaded) {
            mediaManager.downloadAndSaveAvatar(
                parameters.nodeName,
                state.whoAmI.getAvatar(),
                (avatarId, shape) -> {
                    nodeRepository.removeAvatar(parameters.nodeName);
                    nodeRepository.addAvatar(parameters.nodeName, avatarId, shape);
                }
            );
            checkpoint();
        }

        if (state.subscriberId == null) {
            var description = new SubscriberDescription();
            description.setType(SubscriptionType.SEARCH);
            state.subscriberId = nodeApi
                .at(parameters.nodeName, generateCarte(parameters.nodeName, Scope.SUBSCRIBE))
                .createSubscriber(description).getId();
            checkpoint();
        }

        database.writeNoResult(() -> nodeRepository.subscribed(parameters.nodeName, state.subscriberId));

        updateQueue.offer(new PeopleScanUpdate(parameters.nodeName));
        updateQueue.offer(new TimelineScanUpdate(parameters.nodeName));

        database.writeNoResult(() -> nodeRepository.scanSucceeded(parameters.nodeName));
    }

    @Override
    protected void failed() {
        super.failed();
        database.writeNoResult(() -> nodeRepository.scanFailed(parameters.nodeName));
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " for " + parameters.nodeName;
    }

}
