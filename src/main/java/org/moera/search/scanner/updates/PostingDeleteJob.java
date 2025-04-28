package org.moera.search.scanner.updates;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.search.data.NodeRepository;
import org.moera.search.data.PostingRepository;
import org.moera.search.job.Job;
import org.moera.search.scanner.PostingIngest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostingDeleteJob extends Job<PostingDeleteJob.Parameters, Object> {

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

    private static final Logger log = LoggerFactory.getLogger(PostingDeleteJob.class);

    @Inject
    private NodeRepository nodeRepository;

    @Inject
    private PostingRepository postingRepository;

    @Inject
    private PostingIngest postingIngest;

    public PostingDeleteJob() {
        retryCount(5, "PT10M");
    }

    @Override
    protected void setParameters(String parameters, ObjectMapper objectMapper) throws JsonProcessingException {
        this.parameters = objectMapper.readValue(parameters, PostingDeleteJob.Parameters.class);
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
        var exists = database.read(() -> postingRepository.exists(parameters.nodeName, parameters.postingId));
        if (!exists) {
            log.warn("Posting does not exist, skipping");
            return;
        }

        postingIngest.delete(parameters.nodeName, parameters.postingId);
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " for posting " + parameters.postingId + " at node " + parameters.nodeName;
    }

}
