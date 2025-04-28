package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class PostingAddUpdate extends PendingUpdate<PostingAddJob.Parameters> {

    public PostingAddUpdate() {
    }

    public PostingAddUpdate(String nodeName, String postingId) {
        super(new PostingAddJob.Parameters(nodeName, postingId));
    }

    @Override
    protected Class<? extends Job<PostingAddJob.Parameters, ?>> getJobClass() {
        return PostingAddJob.class;
    }

    @Override
    protected Class<PostingAddJob.Parameters> getJobParametersClass() {
        return PostingAddJob.Parameters.class;
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
