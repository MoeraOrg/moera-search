package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class PostingDeleteUpdate extends PendingUpdate<PostingDeleteJob.Parameters> {

    public PostingDeleteUpdate() {
    }

    public PostingDeleteUpdate(String nodeName, String postingId) {
        super(new PostingDeleteJob.Parameters(nodeName, postingId));
    }

    @Override
    protected Class<? extends Job<PostingDeleteJob.Parameters, ?>> getJobClass() {
        return PostingDeleteJob.class;
    }

    @Override
    protected Class<PostingDeleteJob.Parameters> getJobParametersClass() {
        return PostingDeleteJob.Parameters.class;
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
        return JobKeys.postingAllChildren(getJobParameters().getNodeName(), getJobParameters().getPostingId());
    }

}
