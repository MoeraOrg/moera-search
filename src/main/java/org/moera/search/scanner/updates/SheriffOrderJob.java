package org.moera.search.scanner.updates;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.search.data.NodeRepository;
import org.moera.search.job.Job;
import org.moera.search.scanner.ingest.SheriffMarkIngest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SheriffOrderJob extends Job<SheriffOrderJob.Parameters, Object> {

    public static class Parameters {

        private boolean delete;
        private String ownerName;
        private String nodeName;
        private String postingId;
        private String commentId;
        private String sheriffName;

        public Parameters() {
        }

        public Parameters(
            boolean delete, String ownerName, String nodeName, String postingId, String commentId, String sheriffName
        ) {
            this.delete = delete;
            this.ownerName = ownerName;
            this.nodeName = nodeName;
            this.postingId = postingId;
            this.commentId = commentId;
            this.sheriffName = sheriffName;
        }

        public boolean isDelete() {
            return delete;
        }

        public void setDelete(boolean delete) {
            this.delete = delete;
        }

        public String getOwnerName() {
            return ownerName;
        }

        public void setOwnerName(String ownerName) {
            this.ownerName = ownerName;
        }

        public String getNodeName() {
            return nodeName;
        }

        public void setNodeName(String nodeName) {
            this.nodeName = nodeName;
        }

        public String getPostingId() {
            return postingId;
        }

        public void setPostingId(String postingId) {
            this.postingId = postingId;
        }

        public String getCommentId() {
            return commentId;
        }

        public void setCommentId(String commentId) {
            this.commentId = commentId;
        }

        public String getSheriffName() {
            return sheriffName;
        }

        public void setSheriffName(String sheriffName) {
            this.sheriffName = sheriffName;
        }

    }

    private static final Logger log = LoggerFactory.getLogger(SheriffOrderJob.class);

    @Inject
    private NodeRepository nodeRepository;

    @Inject
    private SheriffMarkIngest sheriffMarkIngest;

    public SheriffOrderJob() {
        noRetry();
    }

    @Override
    protected void setParameters(String parameters, ObjectMapper objectMapper) throws JsonProcessingException {
        this.parameters = objectMapper.readValue(parameters, SheriffOrderJob.Parameters.class);
    }

    @Override
    protected void setState(String state, ObjectMapper objectMapper) throws JsonProcessingException {
        this.state = null;
    }

    @Override
    protected void execute() throws Exception {
        var scannedSheriff = database.read(() -> nodeRepository.isScannedSheriff(parameters.sheriffName));
        if (!scannedSheriff) {
            log.warn("Sheriff is not scanned yet, skipping");
            return;
        }

        if (!parameters.delete) {
            sheriffMarkIngest.ingest(
                parameters.sheriffName, parameters.nodeName, parameters.postingId, parameters.commentId,
                parameters.ownerName
            );
        } else {
            sheriffMarkIngest.delete(
                parameters.sheriffName, parameters.nodeName, parameters.postingId, parameters.commentId,
                parameters.ownerName
            );
        }
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " of sheriff " + parameters.sheriffName
            + (!parameters.delete ? " to hide" : " to unhide")
            + (parameters.commentId != null ? " comment " + parameters.commentId + " under " : "")
            + (parameters.postingId != null ? " posting " + parameters.postingId : "")
            + (parameters.ownerName != null ? " owned by " + parameters.ownerName : "")
            + (parameters.nodeName != null ? " at " + parameters.nodeName : "");
    }

}
