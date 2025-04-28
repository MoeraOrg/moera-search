package org.moera.search.scanner.updates;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.search.job.Job;
import org.moera.search.scanner.ingest.PostingIngest;

public class PublicationAddJob extends Job<PublicationAddJob.Parameters, Object> {

    public static class Parameters {

        private String nodeName;
        private String postingId;
        private String publisherName;
        private String feedName;
        private String storyId;
        private long publishedAt;

        public Parameters() {
        }

        public Parameters(
            String nodeName, String postingId, String publisherName, String feedName, String storyId, long publishedAt
        ) {
            this.nodeName = nodeName;
            this.postingId = postingId;
            this.publisherName = publisherName;
            this.feedName = feedName;
            this.storyId = storyId;
            this.publishedAt = publishedAt;
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

        public String getPublisherName() {
            return publisherName;
        }

        public void setPublisherName(String publisherName) {
            this.publisherName = publisherName;
        }

        public String getFeedName() {
            return feedName;
        }

        public void setFeedName(String feedName) {
            this.feedName = feedName;
        }

        public String getStoryId() {
            return storyId;
        }

        public void setStoryId(String storyId) {
            this.storyId = storyId;
        }

        public long getPublishedAt() {
            return publishedAt;
        }

        public void setPublishedAt(long publishedAt) {
            this.publishedAt = publishedAt;
        }

    }

    @Inject
    private PostingIngest postingIngest;

    public PublicationAddJob() {
        retryCount(3, "PT5M");
    }

    @Override
    protected void setParameters(String parameters, ObjectMapper objectMapper) throws JsonProcessingException {
        this.parameters = objectMapper.readValue(parameters, PublicationAddJob.Parameters.class);
    }

    @Override
    protected void setState(String state, ObjectMapper objectMapper) {
        this.state = null;
    }

    @Override
    protected void execute() throws Exception {
        postingIngest.addPublication(
            parameters.nodeName,
            parameters.postingId,
            parameters.publisherName,
            parameters.feedName,
            parameters.storyId,
            parameters.publishedAt
        );
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " for posting " + parameters.postingId + " at node " + parameters.nodeName
            + " published at node " + parameters.publisherName;
    }

}
