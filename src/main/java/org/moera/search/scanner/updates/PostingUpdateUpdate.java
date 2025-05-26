package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class PostingUpdateUpdate extends PendingUpdate<PostingUpdateJob.Parameters> {

    public PostingUpdateUpdate() {
    }

    public PostingUpdateUpdate(String nodeName, String postingId) {
        super(new PostingUpdateJob.Parameters(nodeName, postingId));
    }

    public PostingUpdateUpdate(String nodeName, String postingId, boolean force) {
        super(new PostingUpdateJob.Parameters(nodeName, postingId, force));
    }

    @Override
    protected Class<? extends Job<PostingUpdateJob.Parameters, ?>> getJobClass() {
        return PostingUpdateJob.class;
    }

    @Override
    protected Class<PostingUpdateJob.Parameters> getJobParametersClass() {
        return PostingUpdateJob.Parameters.class;
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
        return JobKeys.posting(getJobParameters().getNodeName(), getJobParameters().getPostingId());
    }

}
