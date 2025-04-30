package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class CommentReactionsDeleteAllUpdate extends PendingUpdate<CommentReactionsDeleteAllJob.Parameters> {

    public CommentReactionsDeleteAllUpdate() {
    }

    public CommentReactionsDeleteAllUpdate(String nodeName, String postingId, String commentId) {
        super(new CommentReactionsDeleteAllJob.Parameters(nodeName, postingId, commentId));
    }

    @Override
    protected Class<? extends Job<CommentReactionsDeleteAllJob.Parameters, ?>> getJobClass() {
        return CommentReactionsDeleteAllJob.class;
    }

    @Override
    protected Class<CommentReactionsDeleteAllJob.Parameters> getJobParametersClass() {
        return CommentReactionsDeleteAllJob.Parameters.class;
    }

    @Override
    public List<String> waitJobKeys() {
        return List.of(
            JobKeys.allContent(getJobParameters().getNodeName()),
            JobKeys.posting(getJobParameters().getNodeName(), getJobParameters().getPostingId()),
            JobKeys.postingAllChildren(getJobParameters().getNodeName(), getJobParameters().getPostingId()),
            JobKeys.comment(
                getJobParameters().getNodeName(), getJobParameters().getPostingId(), getJobParameters().getCommentId()
            ),
            JobKeys.commentAllChildren(
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
