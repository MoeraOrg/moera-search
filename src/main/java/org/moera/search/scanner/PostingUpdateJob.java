package org.moera.search.scanner;

import java.util.Objects;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.types.Scope;
import org.moera.lib.node.types.SearchPostingUpdate;
import org.moera.search.api.NodeApi;
import org.moera.search.job.Job;

public class PostingUpdateJob extends Job<PostingUpdateJob.Parameters, Object> {

    public static class Parameters {

        private String nodeName;
        private SearchPostingUpdate details;

        public Parameters() {
        }

        public Parameters(String nodeName, SearchPostingUpdate details) {
            this.nodeName = nodeName;
            this.details = details;
        }

        public String getNodeName() {
            return nodeName;
        }

        public void setNodeName(String nodeName) {
            this.nodeName = nodeName;
        }

        public SearchPostingUpdate getDetails() {
            return details;
        }

        public void setDetails(SearchPostingUpdate details) {
            this.details = details;
        }

    }

    @Inject
    private NodeApi nodeApi;

    @Inject
    private PostingIngest postingIngest;

    public PostingUpdateJob() {
        retryCount(5, "PT10M");
    }

    @Override
    public String getJobKey() {
        return JobKeys.posting(parameters.details.getNodeName(), parameters.details.getPostingId());
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
        if (Objects.equals(parameters.nodeName, parameters.details.getNodeName())) {
            var posting = nodeApi
                .at(parameters.nodeName, generateCarte(parameters.nodeName, Scope.VIEW_ALL))
                .getPosting(parameters.details.getPostingId(), false);
            if (posting != null) {
                postingIngest.update(parameters.nodeName, posting);
            }
        } else {
            postingIngest.addPublication(
                parameters.details.getNodeName(),
                parameters.details.getPostingId(),
                parameters.nodeName,
                parameters.details.getFeedName(),
                parameters.details.getStoryId(),
                parameters.details.getPublishedAt()
            );
        }
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " for posting " + parameters.details.getPostingId()
            + " from node " + parameters.details.getNodeName() + " published at node " + parameters.nodeName
            + " in feed " + parameters.details.getFeedName() + " as story " + parameters.details.getStoryId();
    }

}
