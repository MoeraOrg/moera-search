package org.moera.search.scanner;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.types.Scope;
import org.moera.search.api.NodeApi;
import org.moera.search.data.PostingRepository;
import org.moera.search.job.Job;

public class PostingScanJob extends Job<PostingScanJob.Parameters, Object> {

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

    @Inject
    private NodeApi nodeApi;

    @Inject
    private PostingRepository postingRepository;

    @Inject
    private PostingIngest postingIngest;

    public PostingScanJob() {
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
        var posting = nodeApi
            .at(parameters.nodeName, generateCarte(parameters.nodeName, Scope.VIEW_ALL))
            .getPosting(parameters.postingId, false);
        if (posting != null) {
            postingIngest.ingest(parameters.nodeName, posting);
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
