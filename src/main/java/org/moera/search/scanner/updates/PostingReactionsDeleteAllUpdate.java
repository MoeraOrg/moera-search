package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class PostingReactionsDeleteAllUpdate extends PendingUpdate<PostingReactionsDeleteAllJob.Parameters> {

    public PostingReactionsDeleteAllUpdate() {
    }

    public PostingReactionsDeleteAllUpdate(String nodeName, String postingId) {
        super(new PostingReactionsDeleteAllJob.Parameters(nodeName, postingId));
    }

    @Override
    protected Class<? extends Job<PostingReactionsDeleteAllJob.Parameters, ?>> getJobClass() {
        return PostingReactionsDeleteAllJob.class;
    }

    @Override
    protected Class<PostingReactionsDeleteAllJob.Parameters> getJobParametersClass() {
        return PostingReactionsDeleteAllJob.Parameters.class;
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
