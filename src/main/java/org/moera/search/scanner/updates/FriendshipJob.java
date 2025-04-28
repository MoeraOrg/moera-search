package org.moera.search.scanner.updates;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.search.job.Job;
import org.moera.search.scanner.NodeIngest;

public class FriendshipJob extends Job<FriendshipJob.Parameters, Object> {

    public static class Parameters {

        private boolean unfriend;
        private String nodeName;
        private String friendName;

        public Parameters() {
        }

        public Parameters(boolean unfriend, String nodeName, String friendName) {
            this.unfriend = unfriend;
            this.nodeName = nodeName;
            this.friendName = friendName;
        }

        public boolean isUnfriend() {
            return unfriend;
        }

        public void setUnfriend(boolean unfriend) {
            this.unfriend = unfriend;
        }

        public String getNodeName() {
            return nodeName;
        }

        public void setNodeName(String nodeName) {
            this.nodeName = nodeName;
        }

        public String getFriendName() {
            return friendName;
        }

        public void setFriendName(String friendName) {
            this.friendName = friendName;
        }

    }

    @Inject
    private NodeIngest nodeIngest;

    public FriendshipJob() {
        noRetry();
    }

    @Override
    protected void setParameters(String parameters, ObjectMapper objectMapper) throws JsonProcessingException {
        this.parameters = objectMapper.readValue(parameters, FriendshipJob.Parameters.class);
    }

    @Override
    protected void setState(String state, ObjectMapper objectMapper) throws JsonProcessingException {
        this.state = null;
    }

    @Override
    protected void execute() throws Exception {
        if (!parameters.unfriend) {
            nodeIngest.friend(parameters.nodeName, parameters.friendName);
        } else {
            nodeIngest.unfriend(parameters.nodeName, parameters.friendName);
        }
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " for " + parameters.nodeName
            + (!parameters.unfriend ? " friends " : " unfriends ") + parameters.friendName;
    }

}
