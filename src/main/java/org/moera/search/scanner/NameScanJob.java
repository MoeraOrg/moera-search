package org.moera.search.scanner;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.exception.MoeraNodeException;
import org.moera.lib.node.types.WhoAmI;
import org.moera.search.api.MoeraNodeUncheckedException;
import org.moera.search.api.NodeApi;
import org.moera.search.data.NodeRepository;
import org.moera.search.job.Job;
import org.moera.search.api.model.AvatarImageUtil;
import org.moera.search.media.MediaManager;

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

    }

    @Inject
    private NodeApi nodeApi;

    @Inject
    private NodeRepository nodeRepository;

    @Inject
    private MediaManager mediaManager;

    public NameScanJob() {
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
        if (state.whoAmI == null) {
            state.whoAmI = nodeApi.at(parameters.nodeName).whoAmI();
            checkpoint();
        }

        if (!state.detailsUpdated) {
            database.executeWriteWithoutResult(() -> nodeRepository.updateName(parameters.nodeName, state.whoAmI));
            state.detailsUpdated = true;
            checkpoint();
        }

        database.executeWriteWithoutResult(() -> {
            try {
                mediaManager.downloadAvatar(parameters.nodeName, state.whoAmI.getAvatar());
            } catch (MoeraNodeException e) {
                throw new MoeraNodeUncheckedException(e);
            }
        });
        if (state.whoAmI.getAvatar() != null && AvatarImageUtil.getMediaFile(state.whoAmI.getAvatar()) != null) {
            var mediaFileId = AvatarImageUtil.getMediaFile(state.whoAmI.getAvatar()).getId();
            String shape = state.whoAmI.getAvatar().getShape();
            database.executeWriteWithoutResult(() -> {
                nodeRepository.removeAvatar(parameters.nodeName);
                nodeRepository.addAvatar(parameters.nodeName, mediaFileId, shape);
            });
        }
    }

    @Override
    protected void failed() {
        super.failed();
        database.executeWriteWithoutResult(() -> nodeRepository.scanFailed(parameters.nodeName));
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " for " + parameters.nodeName;
    }

}
