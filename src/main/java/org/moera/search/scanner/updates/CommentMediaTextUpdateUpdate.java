package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class CommentMediaTextUpdateUpdate extends PendingUpdate<CommentMediaTextUpdateJob.Parameters> {

    public CommentMediaTextUpdateUpdate() {
    }

    public CommentMediaTextUpdateUpdate(
        String nodeName, String postingId, String commentId, String mediaId, String textContent
    ) {
        super(new CommentMediaTextUpdateJob.Parameters(nodeName, postingId, commentId, mediaId, textContent));
    }

    @Override
    protected Class<? extends Job<CommentMediaTextUpdateJob.Parameters, ?>> getJobClass() {
        return CommentMediaTextUpdateJob.class;
    }

    @Override
    protected Class<CommentMediaTextUpdateJob.Parameters> getJobParametersClass() {
        return CommentMediaTextUpdateJob.Parameters.class;
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
