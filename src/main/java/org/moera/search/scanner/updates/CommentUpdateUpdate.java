package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class CommentUpdateUpdate extends PendingUpdate<CommentUpdateJob.Parameters> {

    public CommentUpdateUpdate() {
    }

    public CommentUpdateUpdate(String nodeName, String postingId, String commentId) {
        super(new CommentUpdateJob.Parameters(nodeName, postingId, commentId));
    }

    @Override
    protected Class<? extends Job<CommentUpdateJob.Parameters, ?>> getJobClass() {
        return CommentUpdateJob.class;
    }

    @Override
    protected Class<CommentUpdateJob.Parameters> getJobParametersClass() {
        return CommentUpdateJob.Parameters.class;
    }

    @Override
    public List<String> waitJobKeys() {
        return List.of(
            JobKeys.allContent(getJobParameters().getNodeName()),
            JobKeys.posting(getJobParameters().getNodeName(), getJobParameters().getPostingId()),
            JobKeys.postingAllComments(getJobParameters().getNodeName(), getJobParameters().getPostingId()),
            JobKeys.comment(
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
