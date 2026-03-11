package org.moera.search.scanner.updates;

import jakarta.inject.Inject;

import org.moera.search.job.StatelessJob;
import org.moera.search.scanner.ingest.PostingIngest;
import tools.jackson.databind.ObjectMapper;

public class PublicationDeleteJob extends StatelessJob<PublicationDeleteJob.Parameters> {

    public static class Parameters {

        private String nodeName;
        private String postingId;
        private String publisherName;

        public Parameters() {
        }

        public Parameters(String nodeName, String postingId, String publisherName) {
            this.nodeName = nodeName;
            this.postingId = postingId;
            this.publisherName = publisherName;
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

    }

    @Inject
    private PostingIngest postingIngest;

    public PublicationDeleteJob() {
        retryCount(3, "PT5M");
    }

    @Override
    protected void setParameters(String parameters, ObjectMapper objectMapper) {
        this.parameters = objectMapper.readValue(parameters, PublicationDeleteJob.Parameters.class);
    }

    @Override
    protected void execute() throws Exception {
        postingIngest.deletePublications(
            parameters.nodeName,
            parameters.postingId,
            parameters.publisherName
        );
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " for posting " + parameters.postingId + " from node " + parameters.nodeName
            + " published at node " + parameters.publisherName;
    }

}
