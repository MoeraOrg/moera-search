package org.moera.search.scanner.updates;

import java.util.Objects;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.exception.MoeraNodeException;
import org.moera.lib.node.types.Scope;
import org.moera.search.api.MoeraNodeUncheckedException;
import org.moera.search.api.NodeApi;
import org.moera.search.data.CommentRepository;
import org.moera.search.data.PostingRepository;
import org.moera.search.job.Job;
import org.moera.search.scanner.ingest.CommentIngest;
import org.moera.search.scanner.signature.CommentSignatureVerifier;
import org.moera.search.scanner.signature.SignatureVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommentsScanJob extends Job<CommentsScanJob.Parameters, CommentsScanJob.State> {

    public static class Parameters {

        private String nodeName;
        private String postingId;

        public Parameters() {
        }

        public Parameters(String nodeName, String postingId) {
            this.nodeName = nodeName;
            this.postingId = postingId;
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

    }

    public static class State {

        private long before = Long.MAX_VALUE;
        private String validatedId;

        public State() {
        }

        public long getBefore() {
            return before;
        }

        public void setBefore(long before) {
            this.before = before;
        }

        public String getValidatedId() {
            return validatedId;
        }

        public void setValidatedId(String validatedId) {
            this.validatedId = validatedId;
        }

    }

    private static final Logger log = LoggerFactory.getLogger(CommentsScanJob.class);

    private static final int PAGE_SIZE = 50;

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

    public CommentsScanJob() {
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
        while (state.before > 0) {
            var commentsSlice = nodeApi
                .at(parameters.nodeName, generateCarte(parameters.nodeName, Scope.VIEW_CONTENT))
                .getCommentsSlice(parameters.postingId, null, state.before, PAGE_SIZE);
            for (var comment : commentsSlice.getComments()) {
                state.before = comment.getMoment();
                state.validatedId = null;
                checkpoint();

                // On the next retry the comment may exist (partially), so need to skip the check
                if (!Objects.equals(state.validatedId, comment.getId())) {
                    boolean isAdded = database.read(() ->
                        commentRepository.exists(parameters.nodeName, comment.getPostingId(), comment.getId())
                    );
                    if (isAdded) {
                        continue;
                    }
                    state.validatedId = comment.getId();
                    checkpoint();
                }

                if (comment.getSignature() == null) {
                    log.info("Comment is not signed, skipping");
                    continue;
                }
                try {
                    commentSignatureVerifier.verifySignature(
                        parameters.nodeName,
                        comment,
                        generateCarte(parameters.nodeName, Scope.VIEW_CONTENT)
                    );
                    commentIngest.ingest(
                        parameters.nodeName, comment, carteSupplier(parameters.nodeName, Scope.VIEW_CONTENT)
                    );
                    database.writeNoResult(() ->
                        commentRepository.scanSucceeded(parameters.nodeName, parameters.postingId, comment.getId())
                    );
                } catch (SignatureVerificationException e) {
                    log.error("Incorrect signature of comment {}: {}", comment.getId(), e.getMessage());
                } catch (MoeraNodeException | MoeraNodeUncheckedException e) {
                    if (
                        e instanceof MoeraNodeException ex && isRecoverableError(ex)
                        || e instanceof MoeraNodeUncheckedException ue && isRecoverableError(ue)
                    ) {
                        throw e;
                    }
                    log.error("Cannot verify signature of comment {}: {}", comment.getId(), e.getMessage());
                    log.debug("Cannot verify signature", e);
                }
            }
            state.before = commentsSlice.getAfter();
            checkpoint();
        }

        database.writeNoResult(() ->
            postingRepository.scanCommentsSucceeded(parameters.nodeName, parameters.postingId)
        );
    }

    @Override
    protected void failed() {
        super.failed();
        database.writeNoResult(() -> postingRepository.scanCommentsFailed(parameters.nodeName, parameters.postingId));
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " for posting " + parameters.postingId + " at node " + parameters.nodeName;
    }

}
