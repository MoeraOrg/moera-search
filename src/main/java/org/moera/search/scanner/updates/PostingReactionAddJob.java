package org.moera.search.scanner.updates;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.types.Scope;
import org.moera.search.api.NodeApi;
import org.moera.search.data.PostingRepository;
import org.moera.search.job.Job;
import org.moera.search.scanner.ingest.ReactionIngest;
import org.moera.search.scanner.signature.ReactionSignatureVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostingReactionAddJob extends Job<PostingReactionAddJob.Parameters, Object> {

    public static class Parameters {

        private String nodeName;
        private String postingId;
        private String ownerName;

        public Parameters() {
        }

        public Parameters(String nodeName, String postingId, String ownerName) {
            this.nodeName = nodeName;
            this.postingId = postingId;
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

        public String getOwnerName() {
            return ownerName;
        }

        public void setOwnerName(String ownerName) {
            this.ownerName = ownerName;
        }

    }

    private static final Logger log = LoggerFactory.getLogger(PostingReactionAddJob.class);

    @Inject
    private NodeApi nodeApi;

    @Inject
    private PostingRepository postingRepository;

    @Inject
    private ReactionIngest reactionIngest;

    @Inject
    private ReactionSignatureVerifier reactionSignatureVerifier;

    public PostingReactionAddJob() {
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
            postingRepository.isReactionsScanned(parameters.nodeName, parameters.postingId)
        );
        if (!scannedReactions) {
            log.warn("Reactions are not scanned yet, skipping");
            return;
        }

        var reaction = nodeApi
            .at(parameters.nodeName, generateCarte(parameters.nodeName, Scope.VIEW_CONTENT))
            .getPostingReaction(parameters.postingId, parameters.ownerName);
        if (reaction != null) {
            if (reaction.getSignature() == null) {
                log.info("Reaction is not signed yet, let's wait");
                retry();
            }
            reactionSignatureVerifier.verifySignature(
                parameters.nodeName,
                reaction,
                generateCarte(parameters.nodeName, Scope.VIEW_CONTENT)
            );
            reactionIngest.ingest(parameters.nodeName, reaction);
        }
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " from " + parameters.ownerName + " to posting " + parameters.postingId
            + " at node " + parameters.nodeName;
    }

}
