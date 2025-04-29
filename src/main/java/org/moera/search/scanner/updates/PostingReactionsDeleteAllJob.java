package org.moera.search.scanner.updates;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.search.data.PostingRepository;
import org.moera.search.job.Job;
import org.moera.search.scanner.ingest.ReactionIngest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostingReactionsDeleteAllJob extends Job<PostingReactionsDeleteAllJob.Parameters, Object> {

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

    private static final Logger log = LoggerFactory.getLogger(PostingReactionsDeleteAllJob.class);

    @Inject
    private PostingRepository postingRepository;

    @Inject
    private ReactionIngest reactionIngest;

    public PostingReactionsDeleteAllJob() {
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

        reactionIngest.deleteAll(parameters.nodeName, parameters.postingId);
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " to posting " + parameters.postingId + " at node " + parameters.nodeName;
    }

}
