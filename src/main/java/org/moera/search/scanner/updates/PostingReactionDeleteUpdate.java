package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class PostingReactionDeleteUpdate extends PendingUpdate<PostingReactionDeleteJob.Parameters> {

    public PostingReactionDeleteUpdate() {
    }

    public PostingReactionDeleteUpdate(String nodeName, String postingId, String ownerName) {
        super(new PostingReactionDeleteJob.Parameters(nodeName, postingId, ownerName));
    }

    @Override
    protected Class<? extends Job<PostingReactionDeleteJob.Parameters, ?>> getJobClass() {
        return PostingReactionDeleteJob.class;
    }

    @Override
    protected Class<PostingReactionDeleteJob.Parameters> getJobParametersClass() {
        return PostingReactionDeleteJob.Parameters.class;
    }

    @Override
    public List<String> waitJobKeys() {
        return List.of(
            JobKeys.allContent(getJobParameters().getNodeName()),
            JobKeys.posting(getJobParameters().getNodeName(), getJobParameters().getPostingId()),
            JobKeys.postingAllChildren(getJobParameters().getNodeName(), getJobParameters().getPostingId()),
            JobKeys.postingAllReactions(getJobParameters().getNodeName(), getJobParameters().getPostingId())
        );
    }

    @Override
    public String jobKey() {
        return JobKeys.postingAllReactions(getJobParameters().getNodeName(), getJobParameters().getPostingId());
    }

}
