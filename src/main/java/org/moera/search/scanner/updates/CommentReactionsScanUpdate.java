package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class CommentReactionsScanUpdate extends PendingUpdate<CommentReactionsScanJob.Parameters> {

    public CommentReactionsScanUpdate() {
    }

    public CommentReactionsScanUpdate(String nodeName, String postingId, String commentId) {
        super(new CommentReactionsScanJob.Parameters(nodeName, postingId, commentId));
    }

    @Override
    protected Class<? extends Job<CommentReactionsScanJob.Parameters, ?>> getJobClass() {
        return CommentReactionsScanJob.class;
    }

    @Override
    protected Class<CommentReactionsScanJob.Parameters> getJobParametersClass() {
        return CommentReactionsScanJob.Parameters.class;
    }

    @Override
    public List<String> waitJobKeys() {
        return List.of(
            JobKeys.allContent(getJobParameters().getNodeName()),
            JobKeys.posting(getJobParameters().getNodeName(), getJobParameters().getPostingId()),
            JobKeys.postingAnyChildren(getJobParameters().getNodeName(), getJobParameters().getPostingId()),
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
        return JobKeys.commentAllChildren(
            getJobParameters().getNodeName(), getJobParameters().getPostingId(), getJobParameters().getCommentId()
        );
    }

}
