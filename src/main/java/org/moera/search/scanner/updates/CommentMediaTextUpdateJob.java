package org.moera.search.scanner.updates;

import jakarta.inject.Inject;

import org.moera.search.job.StatelessJob;
import org.moera.search.scanner.ingest.AttachmentIngest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

public class CommentMediaTextUpdateJob extends StatelessJob<CommentMediaTextUpdateJob.Parameters> {

    public static class Parameters {

        private String nodeName;
        private String postingId;
        private String commentId;
        private String mediaId;
        private String title;
        private String textContent;

        public Parameters() {
        }

        public Parameters(
            String nodeName, String postingId, String commentId, String mediaId, String title, String textContent
        ) {
            this.nodeName = nodeName;
            this.postingId = postingId;
            this.commentId = commentId;
            this.mediaId = mediaId;
            this.title = title;
            this.textContent = textContent;
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

        public String getCommentId() {
            return commentId;
        }

        public void setCommentId(String commentId) {
            this.commentId = commentId;
        }

        public String getMediaId() {
            return mediaId;
        }

        public void setMediaId(String mediaId) {
            this.mediaId = mediaId;
        }

        public String getTextContent() {
            return textContent;
        }

        public void setTextContent(String textContent) {
            this.textContent = textContent;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

    }

    public static final Logger log = LoggerFactory.getLogger(CommentMediaTextUpdateJob.class);

    @Inject
    private AttachmentIngest attachmentIngest;

    public CommentMediaTextUpdateJob() {
        retryCount(5, "PT10M");
    }

    @Override
    protected void setParameters(String parameters, ObjectMapper objectMapper) {
        this.parameters = objectMapper.readValue(parameters, CommentMediaTextUpdateJob.Parameters.class);
    }

    @Override
    protected void execute() throws Exception {
        attachmentIngest.updateText(
            parameters.nodeName, parameters.postingId, parameters.commentId, parameters.mediaId,
            parameters.title, parameters.textContent
        );
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " for media " + parameters.mediaId
            + " attached to comment " + parameters.commentId + " under posting " + parameters.postingId
            + " at node " + parameters.nodeName;
    }

}
