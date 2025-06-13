package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class PostingHeadingUpdateUpdate extends PendingUpdate<PostingHeadingUpdateJob.Parameters> {

    public PostingHeadingUpdateUpdate() {
    }

    public PostingHeadingUpdateUpdate(String nodeName, String postingId, String heading) {
        super(new PostingHeadingUpdateJob.Parameters(nodeName, postingId, heading));
    }

    @Override
    protected Class<? extends Job<PostingHeadingUpdateJob.Parameters, ?>> getJobClass() {
        return PostingHeadingUpdateJob.class;
    }

    @Override
    protected Class<PostingHeadingUpdateJob.Parameters> getJobParametersClass() {
        return PostingHeadingUpdateJob.Parameters.class;
    }

    @Override
    public List<String> waitJobKeys() {
        return List.of(
            JobKeys.allContent(getJobParameters().getNodeName()),
            JobKeys.posting(getJobParameters().getNodeName(), getJobParameters().getPostingId())
        );
    }

    @Override
    public String jobKey() {
        return JobKeys.posting(getJobParameters().getNodeName(), getJobParameters().getPostingId());
    }

}
