package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class PostingReactionAddUpdate extends PendingUpdate<PostingReactionAddJob.Parameters> {

    public PostingReactionAddUpdate() {
    }

    public PostingReactionAddUpdate(String nodeName, String postingId, String ownerName) {
        super(new PostingReactionAddJob.Parameters(nodeName, postingId, ownerName));
    }

    @Override
    protected Class<? extends Job<PostingReactionAddJob.Parameters, ?>> getJobClass() {
        return PostingReactionAddJob.class;
    }

    @Override
    protected Class<PostingReactionAddJob.Parameters> getJobParametersClass() {
        return PostingReactionAddJob.Parameters.class;
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
