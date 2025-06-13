package org.moera.search.scanner.updates;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.search.data.PostingRepository;
import org.moera.search.job.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostingHeadingUpdateJob extends Job<PostingHeadingUpdateJob.Parameters, Object> {

    public static class Parameters {

        private String nodeName;
        private String postingId;
        private String heading;

        public Parameters() {
        }

        public Parameters(String nodeName, String postingId, String heading) {
            this.nodeName = nodeName;
            this.postingId = postingId;
            this.heading = heading;
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

        public String getHeading() {
            return heading;
        }

        public void setHeading(String heading) {
            this.heading = heading;
        }

    }

    public static final Logger log = LoggerFactory.getLogger(PostingHeadingUpdateJob.class);

    @Inject
    private PostingRepository postingRepository;

    public PostingHeadingUpdateJob() {
        noRetry();
    }

    @Override
    protected void setParameters(String parameters, ObjectMapper objectMapper) throws JsonProcessingException {
        this.parameters = objectMapper.readValue(parameters, PostingHeadingUpdateJob.Parameters.class);
    }

    @Override
    protected void setState(String state, ObjectMapper objectMapper) {
        this.state = null;
    }

    @Override
    protected void execute() throws Exception {
        database.writeNoResult(() ->
            postingRepository.setHeading(parameters.nodeName, parameters.postingId, parameters.heading)
        );
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " for posting " + parameters.postingId + " at node " + parameters.nodeName;
    }

}
