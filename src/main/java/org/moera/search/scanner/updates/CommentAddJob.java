package org.moera.search.scanner.updates;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.types.Scope;
import org.moera.search.api.NodeApi;
import org.moera.search.data.CommentRepository;
import org.moera.search.data.PostingRepository;
import org.moera.search.job.Job;
import org.moera.search.scanner.ingest.CommentIngest;
import org.moera.search.scanner.signature.CommentSignatureVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommentAddJob extends Job<CommentAddJob.Parameters, CommentAddJob.State> {

    public static class Parameters {

        private String nodeName;
        private String postingId;
        private String commentId;

        public Parameters() {
        }

        public Parameters(String nodeName, String postingId, String commentId) {
            this.nodeName = nodeName;
            this.postingId = postingId;
            this.commentId = commentId;
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

    }

    public static class State {

        private boolean validated;

        public State() {
        }

        public boolean isValidated() {
            return validated;
        }

        public void setValidated(boolean validated) {
            this.validated = validated;
        }

    }

    private static final Logger log = LoggerFactory.getLogger(CommentAddJob.class);

    @Inject
    private NodeApi nodeApi;

    @Inject
    private PostingRepository postingRepository;

    @Inject
    private CommentRepository commentRepository;

    @Inject
    private CommentIngest commentIngest;

    @Inject
    private CommentSignatureVerifier commentSignatureVerifier;

    public CommentAddJob() {
        state = new State();
        retryCount(5, "PT10M");
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
        // On the next retry the comment may exist (partially), so need to skip the check
        if (!state.validated) {
            var scannedComments = database.read(() ->
                postingRepository.isCommentsScanned(parameters.nodeName, parameters.postingId)
            );
            if (!scannedComments) {
                log.warn("Comments are not scanned yet, skipping");
                return;
            }
            var exists = database.read(() ->
                commentRepository.exists(parameters.nodeName, parameters.postingId, parameters.commentId)
            );
            if (exists) {
                log.warn("Comment is added already, skipping");
                return;
            }
            state.validated = true;
            checkpoint();
        }

        var comment = nodeApi
            .at(parameters.nodeName, generateCarte(parameters.nodeName, Scope.VIEW_CONTENT))
            .getComment(parameters.postingId, parameters.commentId, false);
        if (comment != null) {
            if (comment.getSignature() == null) {
                log.info("Comment is not signed yet, let's wait");
                retry();
            }
            commentSignatureVerifier.verifySignature(
                parameters.nodeName,
                comment,
                generateCarte(parameters.nodeName, Scope.VIEW_CONTENT)
            );
            commentIngest.ingest(parameters.nodeName, comment);
        }

        database.writeNoResult(() ->
            commentRepository.scanSucceeded(parameters.nodeName, parameters.postingId, parameters.commentId)
        );
    }

    @Override
    protected void failed() {
        super.failed();
        database.writeNoResult(() ->
            commentRepository.scanFailed(parameters.nodeName, parameters.postingId, parameters.commentId)
        );
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " for comment " + parameters.commentId
            + " under posting " + parameters.postingId + " at node " + parameters.nodeName;
    }

}
