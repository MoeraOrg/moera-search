package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class CommentReactionDeleteUpdate extends PendingUpdate<CommentReactionDeleteJob.Parameters> {

    public CommentReactionDeleteUpdate() {
    }

    public CommentReactionDeleteUpdate(String nodeName, String postingId, String commentId, String ownerName) {
        super(new CommentReactionDeleteJob.Parameters(nodeName, postingId, commentId, ownerName));
    }

    @Override
    protected Class<? extends Job<CommentReactionDeleteJob.Parameters, ?>> getJobClass() {
        return CommentReactionDeleteJob.class;
    }

    @Override
    protected Class<CommentReactionDeleteJob.Parameters> getJobParametersClass() {
        return CommentReactionDeleteJob.Parameters.class;
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
