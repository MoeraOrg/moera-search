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

public class PostingUpdateJob extends Job<PostingUpdateJob.Parameters, Object> {

    public static class Parameters {

        private String nodeName;
        private String postingId;
        private boolean force;

        public Parameters() {
        }

        public Parameters(String nodeName, String postingId) {
            this(nodeName, postingId, false);
        }

        public Parameters(String nodeName, String postingId, boolean force) {
            this.nodeName = nodeName;
            this.postingId = postingId;
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

        public boolean isForce() {
            return force;
        }

        public void setForce(boolean force) {
            this.force = force;
        }

    }

    public static final Logger log = LoggerFactory.getLogger(PostingUpdateJob.class);

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

    public PostingUpdateJob() {
        retryCount(5, "PT10M");
    }

    @Override
    protected void setParameters(String parameters, ObjectMapper objectMapper) throws JsonProcessingException {
        this.parameters = objectMapper.readValue(parameters, PostingUpdateJob.Parameters.class);
    }

    @Override
    protected void setState(String state, ObjectMapper objectMapper) {
        this.state = null;
    }

    @Override
    protected void execute() throws Exception {
        var scannedTimeline = database.read(() -> nodeRepository.isScannedTimeline(parameters.nodeName));
        if (!scannedTimeline) {
            log.warn("Timeline is not scanned yet, skipping");
            return;
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
            var exists = database.read(() -> postingRepository.exists(parameters.nodeName, parameters.postingId));
            if (!exists) {
                postingIngest.ingest(
                    parameters.nodeName, posting, carteSupplier(parameters.nodeName, Scope.VIEW_CONTENT)
                );
                database.writeNoResult(() ->
                    postingRepository.scanSucceeded(parameters.nodeName, parameters.postingId)
                );
            } else {
                postingIngest.update(
                    parameters.nodeName, posting, carteSupplier(parameters.nodeName, Scope.VIEW_CONTENT),
                    parameters.force
                );
            }
        }
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " for posting " + parameters.postingId + " at node " + parameters.nodeName;
    }

}
