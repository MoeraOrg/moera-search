package org.moera.search.scanner.updates;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.types.Scope;
import org.moera.search.api.NodeApi;
import org.moera.search.data.CommentRepository;
import org.moera.search.job.Job;
import org.moera.search.scanner.ingest.ReactionIngest;

public class CommentReactionsScanJob extends Job<CommentReactionsScanJob.Parameters, CommentReactionsScanJob.State> {

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

        private long before = Long.MAX_VALUE;

        public State() {
        }

        public long getBefore() {
            return before;
        }

        public void setBefore(long before) {
            this.before = before;
        }

    }

    private static final int PAGE_SIZE = 50;

    @Inject
    private NodeApi nodeApi;

    @Inject
    private CommentRepository commentRepository;

    @Inject
    private ReactionIngest reactionIngest;

    public CommentReactionsScanJob() {
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
            var reactionsSlice = nodeApi
                .at(parameters.nodeName, generateCarte(parameters.nodeName, Scope.VIEW_CONTENT))
                .getCommentReactionsSlice(
                    parameters.postingId, parameters.commentId, null, null, state.before, PAGE_SIZE
                );
            for (var reaction : reactionsSlice.getReactions()) {
                state.before = reaction.getMoment();
                checkpoint();

                reactionIngest.ingest(parameters.nodeName, reaction);
            }
            state.before = reactionsSlice.getAfter();
            checkpoint();
        }

        database.writeNoResult(() ->
            commentRepository.scanReactionsSucceeded(parameters.nodeName, parameters.postingId, parameters.commentId)
        );
    }

    @Override
    protected void failed() {
        super.failed();
        database.writeNoResult(() ->
            commentRepository.scanReactionsFailed(parameters.nodeName, parameters.postingId, parameters.commentId)
        );
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " for comment " + parameters.commentId
            + " to posting " + parameters.postingId + " at node " + parameters.nodeName;
    }

}
