package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class CommentHeadingUpdateUpdate extends PendingUpdate<CommentHeadingUpdateJob.Parameters> {

    public CommentHeadingUpdateUpdate() {
    }

    public CommentHeadingUpdateUpdate(String nodeName, String postingId, String commentId, String heading) {
        super(new CommentHeadingUpdateJob.Parameters(nodeName, postingId, commentId, heading));
    }

    @Override
    protected Class<? extends Job<CommentHeadingUpdateJob.Parameters, ?>> getJobClass() {
        return CommentHeadingUpdateJob.class;
    }

    @Override
    protected Class<CommentHeadingUpdateJob.Parameters> getJobParametersClass() {
        return CommentHeadingUpdateJob.Parameters.class;
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
