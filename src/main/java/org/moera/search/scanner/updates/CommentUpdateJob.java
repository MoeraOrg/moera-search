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

public class CommentUpdateJob extends Job<CommentUpdateJob.Parameters, Object> {

    public static class Parameters {

        private String nodeName;
        private String postingId;
        private String commentId;
        private boolean force;

        public Parameters() {
        }

        public Parameters(String nodeName, String postingId, String commentId) {
            this(nodeName, postingId, commentId, false);
        }

        public Parameters(String nodeName, String postingId, String commentId, boolean force) {
            this.nodeName = nodeName;
            this.postingId = postingId;
            this.commentId = commentId;
            this.force = force;
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

        public boolean isForce() {
            return force;
        }

        public void setForce(boolean force) {
            this.force = force;
        }

    }

    private static final Logger log = LoggerFactory.getLogger(CommentUpdateJob.class);

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

    public CommentUpdateJob() {
        retryCount(5, "PT10M");
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
        var scannedComments = database.read(() ->
            postingRepository.isCommentsScanned(parameters.nodeName, parameters.postingId)
        );
        if (!scannedComments) {
            log.warn("Comments are not scanned yet, skipping");
            return;
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
            var exists = database.read(() ->
                commentRepository.exists(parameters.nodeName, parameters.postingId, parameters.commentId)
            );
            if (!exists) {
                commentIngest.ingest(
                    parameters.nodeName, comment, carteSupplier(parameters.nodeName, Scope.VIEW_CONTENT)
                );
                database.writeNoResult(() ->
                    commentRepository.scanSucceeded(parameters.nodeName, parameters.postingId, parameters.commentId)
                );
            } else {
                commentIngest.update(
                    parameters.nodeName, comment, carteSupplier(parameters.nodeName, Scope.VIEW_CONTENT),
                    parameters.force
                );
            }
        }
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " for comment " + parameters.commentId
            + " under posting " + parameters.postingId + " at node " + parameters.nodeName;
    }

}
