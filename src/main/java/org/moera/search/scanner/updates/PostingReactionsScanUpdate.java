package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class PostingReactionsScanUpdate extends PendingUpdate<PostingReactionsScanJob.Parameters> {

    public PostingReactionsScanUpdate() {
    }

    public PostingReactionsScanUpdate(String nodeName, String postingId) {
        super(new PostingReactionsScanJob.Parameters(nodeName, postingId));
    }

    @Override
    protected Class<? extends Job<PostingReactionsScanJob.Parameters, ?>> getJobClass() {
        return PostingReactionsScanJob.class;
    }

    @Override
    protected Class<PostingReactionsScanJob.Parameters> getJobParametersClass() {
        return PostingReactionsScanJob.Parameters.class;
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
        return JobKeys.postingAllReactions(getJobParameters().getNodeName(), getJobParameters().getPostingId());
    }

}
