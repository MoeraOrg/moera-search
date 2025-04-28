package org.moera.search.scanner.updates;

import java.util.List;

import jakarta.inject.Inject;

import org.moera.search.data.CommentRepository;
import org.moera.search.data.Database;
import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class CommentRepliedToUpdate extends PendingUpdate<CommentRepliedToJob.Parameters> {

    @Inject
    private Database database;

    @Inject
    private CommentRepository commentRepository;

    public CommentRepliedToUpdate() {
    }

    public CommentRepliedToUpdate(String nodeName, String postingId, String commentId, String repliedToId) {
        super(new CommentRepliedToJob.Parameters(nodeName, postingId, commentId, repliedToId));
    }

    @Override
    protected Class<? extends Job<CommentRepliedToJob.Parameters, ?>> getJobClass() {
        return CommentRepliedToJob.class;
    }

    @Override
    protected Class<CommentRepliedToJob.Parameters> getJobParametersClass() {
        return CommentRepliedToJob.Parameters.class;
    }

    @Override
    public boolean isPrepared() {
        return database.read(() ->
            commentRepository.exists(
                getJobParameters().getNodeName(), getJobParameters().getPostingId(), getJobParameters().getRepliedToId()
            )
        );
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
            JobKeys.comment(
                getJobParameters().getNodeName(), getJobParameters().getPostingId(), getJobParameters().getRepliedToId()
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
