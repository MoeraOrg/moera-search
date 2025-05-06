package org.moera.search.scanner.updates;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;

import org.moera.search.data.CommentRepository;
import org.moera.search.data.Database;
import org.moera.search.data.PendingUpdate;
import org.moera.search.data.PostingRepository;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class SheriffOrderUpdate extends PendingUpdate<SheriffOrderJob.Parameters> {

    private final List<String> waitJobKeys;

    @Inject
    private Database database;

    @Inject
    private PostingRepository postingRepository;

    @Inject
    private CommentRepository commentRepository;

    public SheriffOrderUpdate() {
        waitJobKeys = null;
    }

    public SheriffOrderUpdate(
        boolean delete, String ownerName, String nodeName, String postingId, String commentId, String sheriffName
    ) {
        super(new SheriffOrderJob.Parameters(delete, ownerName, nodeName, postingId, commentId, sheriffName));

        waitJobKeys = new ArrayList<>();
        waitJobKeys.add(JobKeys.sheriff(getJobParameters().getSheriffName()));
        if (getJobParameters().getPostingId() != null) {
            waitJobKeys.add(JobKeys.allContent(getJobParameters().getNodeName()));
            waitJobKeys.add(JobKeys.posting(getJobParameters().getNodeName(), getJobParameters().getPostingId()));
        }
        if (getJobParameters().getCommentId() != null) {
            waitJobKeys.add(JobKeys.comment(
                getJobParameters().getNodeName(), getJobParameters().getPostingId(), getJobParameters().getCommentId()
            ));
        }
    }

    @Override
    protected Class<? extends Job<SheriffOrderJob.Parameters, ?>> getJobClass() {
        return SheriffOrderJob.class;
    }

    @Override
    protected Class<SheriffOrderJob.Parameters> getJobParametersClass() {
        return SheriffOrderJob.Parameters.class;
    }

    @Override
    public boolean isPrepared() {
        boolean prepared = true;
        if (getJobParameters().getPostingId() != null) {
            prepared = database.read(() ->
                postingRepository.exists(getJobParameters().getNodeName(), getJobParameters().getPostingId())
            );
        }
        if (getJobParameters().getCommentId() != null) {
            prepared = prepared && database.read(() ->
                commentRepository.exists(
                    getJobParameters().getNodeName(),
                    getJobParameters().getPostingId(),
                    getJobParameters().getCommentId()
                )
            );
        }
        return prepared;
    }

    @Override
    public List<String> waitJobKeys() {
        return waitJobKeys;
    }

    @Override
    public String jobKey() {
        return JobKeys.sheriff(getJobParameters().getSheriffName());
    }

}
