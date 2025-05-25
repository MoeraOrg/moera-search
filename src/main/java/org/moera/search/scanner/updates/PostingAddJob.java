package org.moera.search.scanner.updates;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.types.Scope;
import org.moera.search.api.NodeApi;
import org.moera.search.data.NodeRepository;
import org.moera.search.data.PostingRepository;
import org.moera.search.job.Job;
import org.moera.search.scanner.ingest.PostingIngest;
import org.moera.search.scanner.signature.PostingSignatureVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostingAddJob extends Job<PostingAddJob.Parameters, PostingAddJob.State> {

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

    private static final Logger log = LoggerFactory.getLogger(PostingAddJob.class);

    @Inject
    private NodeApi nodeApi;

    @Inject
    private NodeRepository nodeRepository;

    @Inject
    private PostingRepository postingRepository;

    @Inject
    private PostingIngest postingIngest;

    @Inject
    private PostingSignatureVerifier postingSignatureVerifier;

    public PostingAddJob() {
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
        // On the next retry the posting may exist (partially), so need to skip the check
        if (!state.validated) {
            var scannedTimeline = database.read(() -> nodeRepository.isScannedTimeline(parameters.nodeName));
            if (!scannedTimeline) {
                log.warn("Timeline is not scanned yet, skipping");
                return;
            }
            var exists = database.read(() -> postingRepository.exists(parameters.nodeName, parameters.postingId));
            if (exists) {
                log.warn("Posting is added already, skipping");
                return;
            }
            state.validated = true;
            checkpoint();
        }

        var posting = nodeApi
            .at(parameters.nodeName, generateCarte(parameters.nodeName, Scope.VIEW_CONTENT))
            .getPosting(parameters.postingId, false);
        if (posting != null) {
            if (posting.getSignature() == null) {
                log.info("Posting is not signed yet, let's wait");
                retry();
            }
            postingSignatureVerifier.verifySignature(
                parameters.nodeName,
                posting,
                generateCarte(parameters.nodeName, Scope.VIEW_CONTENT)
            );
            postingIngest.ingest(parameters.nodeName, posting, carteSupplier(parameters.nodeName, Scope.VIEW_CONTENT));
        }

        database.writeNoResult(() -> postingRepository.scanSucceeded(parameters.nodeName, parameters.postingId));
    }

    @Override
    protected void failed() {
        super.failed();
        database.writeNoResult(() -> postingRepository.scanFailed(parameters.nodeName, parameters.postingId));
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " for posting " + parameters.postingId + " at node " + parameters.nodeName;
    }

}
