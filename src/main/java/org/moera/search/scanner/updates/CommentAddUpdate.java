package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class CommentAddUpdate extends PendingUpdate<CommentAddJob.Parameters> {

    public CommentAddUpdate() {
    }

    public CommentAddUpdate(String nodeName, String postingId, String commentId) {
        super(new CommentAddJob.Parameters(nodeName, postingId, commentId));
    }

    @Override
    protected Class<? extends Job<CommentAddJob.Parameters, ?>> getJobClass() {
        return CommentAddJob.class;
    }

    @Override
    protected Class<CommentAddJob.Parameters> getJobParametersClass() {
        return CommentAddJob.Parameters.class;
    }

    @Override
    public List<String> waitJobKeys() {
        return List.of(
            JobKeys.allContent(getJobParameters().getNodeName()),
            JobKeys.posting(getJobParameters().getNodeName(), getJobParameters().getPostingId()),
            JobKeys.postingAllComments(getJobParameters().getNodeName(), getJobParameters().getPostingId()),
            JobKeys.comment(
                getJobParameters().getNodeName(), getJobParameters().getPostingId(), getJobParameters().getCommentId()
            ),
            JobKeys.commentAnyChildren(
                getJobParameters().getNodeName(), getJobParameters().getPostingId(), getJobParameters().getCommentId()
            )
        );
    }

    @Override
    public String jobKey() {
        return JobKeys.comment(
            getJobParameters().getNodeName(), getJobParameters().getPostingId(), getJobParameters().getCommentId()
        );
    }

}
