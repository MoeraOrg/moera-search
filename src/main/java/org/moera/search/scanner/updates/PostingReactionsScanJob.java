package org.moera.search.scanner.updates;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.exception.MoeraNodeException;
import org.moera.lib.node.types.Scope;
import org.moera.search.api.MoeraNodeUncheckedException;
import org.moera.search.api.NodeApi;
import org.moera.search.data.PostingRepository;
import org.moera.search.job.Job;
import org.moera.search.scanner.ingest.ReactionIngest;
import org.moera.search.scanner.signature.ReactionSignatureVerifier;
import org.moera.search.scanner.signature.SignatureVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostingReactionsScanJob extends Job<PostingReactionsScanJob.Parameters, PostingReactionsScanJob.State> {

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

        public State() {
        }

        public long getBefore() {
            return before;
        }

        public void setBefore(long before) {
            this.before = before;
        }

    }

    private static final Logger log = LoggerFactory.getLogger(PostingReactionsScanJob.class);

    private static final int PAGE_SIZE = 50;

    @Inject
    private NodeApi nodeApi;

    @Inject
    private PostingRepository postingRepository;

    @Inject
    private ReactionIngest reactionIngest;

    @Inject
    private ReactionSignatureVerifier reactionSignatureVerifier;

    public PostingReactionsScanJob() {
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
                .getPostingReactionsSlice(parameters.postingId, null, null, state.before, PAGE_SIZE);
            for (var reaction : reactionsSlice.getReactions()) {
                state.before = reaction.getMoment();
                checkpoint();

                if (reaction.getSignature() == null) {
                    log.info("Reaction is not signed, skipping");
                    continue;
                }
                try {
                    reactionSignatureVerifier.verifySignature(
                        parameters.nodeName,
                        reaction,
                        generateCarte(parameters.nodeName, Scope.VIEW_CONTENT)
                    );
                    reactionIngest.ingest(parameters.nodeName, reaction);
                } catch (SignatureVerificationException e) {
                    log.error("Incorrect signature of reaction by {}: {}", reaction.getOwnerName(), e.getMessage());
                } catch (MoeraNodeException | MoeraNodeUncheckedException e) {
                    if (
                        e instanceof MoeraNodeException ex && isRecoverableError(ex)
                        || e instanceof MoeraNodeUncheckedException ue && isRecoverableError(ue)
                    ) {
                        throw e;
                    }
                    log.error("Cannot verify signature of reaction by {}: {}", reaction.getOwnerName(), e.getMessage());
                    log.debug("Cannot verify signature", e);
                }
            }
            state.before = reactionsSlice.getAfter();
            checkpoint();
        }

        database.writeNoResult(() ->
            postingRepository.scanReactionsSucceeded(parameters.nodeName, parameters.postingId)
        );
    }

    @Override
    protected void failed() {
        super.failed();
        database.writeNoResult(() -> postingRepository.scanReactionsFailed(parameters.nodeName, parameters.postingId));
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " for posting " + parameters.postingId + " at node " + parameters.nodeName;
    }

}
