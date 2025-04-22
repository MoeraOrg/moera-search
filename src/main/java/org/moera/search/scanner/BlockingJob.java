package org.moera.search.scanner;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.types.BlockedOperation;
import org.moera.search.job.Job;

public class BlockingJob extends Job<BlockingJob.Parameters, Object> {

    public static class Parameters {

        private boolean unblocks;
        private String nodeName;
        private String blockedName;
        private BlockedOperation operation;

        public Parameters() {
        }

        public Parameters(boolean unblocks, String nodeName, String blockedName, BlockedOperation operation) {
            this.unblocks = unblocks;
            this.nodeName = nodeName;
            this.blockedName = blockedName;
            this.operation = operation;
        }

        public boolean isUnblocks() {
            return unblocks;
        }

        public void setUnblocks(boolean unblocks) {
            this.unblocks = unblocks;
        }

        public String getNodeName() {
            return nodeName;
        }

        public void setNodeName(String nodeName) {
            this.nodeName = nodeName;
        }

        public String getBlockedName() {
            return blockedName;
        }

        public void setBlockedName(String blockedName) {
            this.blockedName = blockedName;
        }

        public BlockedOperation getOperation() {
            return operation;
        }

        public void setOperation(BlockedOperation operation) {
            this.operation = operation;
        }

    }

    @Inject
    private NodeIngest nodeIngest;

    public BlockingJob() {
        noRetry();
    }

    @Override
    public String getJobKey() {
        return JobKeys.nodeRelative(parameters.blockedName);
    }

    @Override
    protected void setParameters(String parameters, ObjectMapper objectMapper) throws JsonProcessingException {
        this.parameters = objectMapper.readValue(parameters, BlockingJob.Parameters.class);
    }

    @Override
    protected void setState(String state, ObjectMapper objectMapper) throws JsonProcessingException {
        this.state = null;
    }

    @Override
    protected void execute() throws Exception {
        if (!parameters.unblocks) {
            nodeIngest.blocks(parameters.nodeName, parameters.blockedName, parameters.operation);
        } else {
            nodeIngest.unblocks(parameters.nodeName, parameters.blockedName, parameters.operation);
        }
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " for " + parameters.nodeName
            + (!parameters.unblocks ? " blocks " : " unblocks ") + parameters.getOperation().getValue()
            + " from " + parameters.blockedName;
    }

}
