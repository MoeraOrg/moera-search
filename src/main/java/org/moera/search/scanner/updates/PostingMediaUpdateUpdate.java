package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class PostingMediaUpdateUpdate extends PendingUpdate<PostingMediaUpdateJob.Parameters> {

    public PostingMediaUpdateUpdate() {
    }

    public PostingMediaUpdateUpdate(
        String nodeName, String postingId, String mediaId, String remoteMediaNodeName, String remoteMediaId,
        String title, String textContent
    ) {
        super(new PostingMediaUpdateJob.Parameters(
            nodeName, postingId, mediaId, remoteMediaNodeName, remoteMediaId, title, textContent
        ));
    }

    @Override
    protected Class<? extends Job<PostingMediaUpdateJob.Parameters, ?>> getJobClass() {
        return PostingMediaUpdateJob.class;
    }

    @Override
    protected Class<PostingMediaUpdateJob.Parameters> getJobParametersClass() {
        return PostingMediaUpdateJob.Parameters.class;
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
