package org.moera.search.scanner.updates;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.search.data.CommentRepository;
import org.moera.search.job.Job;

public class CommentRepliedToJob extends Job<CommentRepliedToJob.Parameters, Object> {

    public static class Parameters {

        private String nodeName;
        private String postingId;
        private String commentId;
        private String repliedToId;

        public Parameters() {
        }

        public Parameters(String nodeName, String postingId, String commentId, String repliedToId) {
            this.nodeName = nodeName;
            this.postingId = postingId;
            this.commentId = commentId;
            this.repliedToId = repliedToId;
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

        public String getRepliedToId() {
            return repliedToId;
        }

        public void setRepliedToId(String repliedToId) {
            this.repliedToId = repliedToId;
        }

    }

    @Inject
    private CommentRepository commentRepository;

    public CommentRepliedToJob() {
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
        database.writeNoResult(() ->
            commentRepository.assignCommentRepliedTo(
                parameters.nodeName, parameters.postingId, parameters.commentId, parameters.repliedToId
            )
        );
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " for comment " + parameters.commentId
            + " replying to " + parameters.repliedToId + " under posting " + parameters.postingId
            + " at node " + parameters.nodeName;
    }

}
