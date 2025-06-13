package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class PostingMediaTextUpdateUpdate extends PendingUpdate<PostingMediaTextUpdateJob.Parameters> {

    public PostingMediaTextUpdateUpdate() {
    }

    public PostingMediaTextUpdateUpdate(String nodeName, String postingId, String mediaId, String textContent) {
        super(new PostingMediaTextUpdateJob.Parameters(nodeName, postingId, mediaId, textContent));
    }

    @Override
    protected Class<? extends Job<PostingMediaTextUpdateJob.Parameters, ?>> getJobClass() {
        return PostingMediaTextUpdateJob.class;
    }

    @Override
    protected Class<PostingMediaTextUpdateJob.Parameters> getJobParametersClass() {
        return PostingMediaTextUpdateJob.Parameters.class;
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
