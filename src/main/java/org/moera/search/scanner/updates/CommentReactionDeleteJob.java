package org.moera.search.scanner.updates;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.search.data.CommentRepository;
import org.moera.search.job.Job;
import org.moera.search.scanner.ingest.ReactionIngest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommentReactionDeleteJob extends Job<CommentReactionDeleteJob.Parameters, Object> {

    public static class Parameters {

        private String nodeName;
        private String postingId;
        private String commentId;
        private String ownerName;

        public Parameters() {
        }

        public Parameters(String nodeName, String postingId, String commentId, String ownerName) {
            this.nodeName = nodeName;
            this.postingId = postingId;
            this.commentId = commentId;
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

        public String getOwnerName() {
            return ownerName;
        }

        public void setOwnerName(String ownerName) {
            this.ownerName = ownerName;
        }

    }

    private static final Logger log = LoggerFactory.getLogger(CommentReactionDeleteJob.class);

    @Inject
    private CommentRepository commentRepository;

    @Inject
    private ReactionIngest reactionIngest;

    public CommentReactionDeleteJob() {
        retryCount(3, "PT10M");
    }

    @Override
    protected void setParameters(String parameters, ObjectMapper objectMapper) throws JsonProcessingException {
        this.parameters = objectMapper.readValue(parameters, Parameters.class);
    }

    @Override
    protected void setState(String state, ObjectMapper objectMapper) {
        this.state = null;
    }

    @Override
    protected void execute() throws Exception {
        var scannedReactions = database.read(() ->
            commentRepository.isReactionsScanned(parameters.nodeName, parameters.postingId, parameters.commentId)
        );
        if (!scannedReactions) {
            log.warn("Reactions are not scanned yet, skipping");
            return;
        }

        reactionIngest.delete(parameters.nodeName, parameters.postingId, parameters.commentId, parameters.ownerName);
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " from " + parameters.ownerName + " to comment " + parameters.commentId
            + " to posting " + parameters.postingId + " at node " + parameters.nodeName;
    }

}
