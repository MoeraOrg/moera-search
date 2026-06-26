package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class CommentMediaUpdateUpdate extends PendingUpdate<CommentMediaUpdateJob.Parameters> {

    public CommentMediaUpdateUpdate() {
    }

    public CommentMediaUpdateUpdate(
        String nodeName, String postingId, String commentId, String mediaId, String remoteMediaNodeName,
        String remoteMediaId, String title, String textContent
    ) {
        super(new CommentMediaUpdateJob.Parameters(
            nodeName, postingId, commentId, mediaId, remoteMediaNodeName, remoteMediaId, title, textContent
        ));
    }

    @Override
    protected Class<? extends Job<CommentMediaUpdateJob.Parameters, ?>> getJobClass() {
        return CommentMediaUpdateJob.class;
    }

    @Override
    protected Class<CommentMediaUpdateJob.Parameters> getJobParametersClass() {
        return CommentMediaUpdateJob.Parameters.class;
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
