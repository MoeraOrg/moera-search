package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class CommentsScanUpdate extends PendingUpdate<CommentsScanJob.Parameters> {

    public CommentsScanUpdate() {
    }

    public CommentsScanUpdate(String nodeName, String postingId) {
        super(new CommentsScanJob.Parameters(nodeName, postingId));
    }

    @Override
    protected Class<? extends Job<CommentsScanJob.Parameters, ?>> getJobClass() {
        return CommentsScanJob.class;
    }

    @Override
    protected Class<CommentsScanJob.Parameters> getJobParametersClass() {
        return CommentsScanJob.Parameters.class;
    }

    @Override
    public List<String> waitJobKeys() {
        return List.of(
            JobKeys.allContent(getJobParameters().getNodeName()),
            JobKeys.posting(getJobParameters().getNodeName(), getJobParameters().getPostingId()),
            JobKeys.postingAnyChildren(getJobParameters().getNodeName(), getJobParameters().getPostingId())
        );
    }

    @Override
    public String jobKey() {
        return JobKeys.postingAllComments(getJobParameters().getNodeName(), getJobParameters().getPostingId());
    }

}
