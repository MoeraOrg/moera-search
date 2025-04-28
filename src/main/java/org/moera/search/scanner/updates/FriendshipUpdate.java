package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class FriendshipUpdate extends PendingUpdate<FriendshipJob.Parameters> {

    public FriendshipUpdate() {
    }

    public FriendshipUpdate(boolean unfriend, String nodeName, String friendName) {
        super(new FriendshipJob.Parameters(unfriend, nodeName, friendName));
    }

    @Override
    protected Class<? extends Job<FriendshipJob.Parameters, ?>> getJobClass() {
        return FriendshipJob.class;
    }

    @Override
    protected Class<FriendshipJob.Parameters> getJobParametersClass() {
        return FriendshipJob.Parameters.class;
    }

    @Override
    public List<String> waitJobKeys() {
        return List.of(JobKeys.nodeRelatives(getJobParameters().getNodeName()));
    }

    @Override
    public String jobKey() {
        return JobKeys.nodeRelatives(getJobParameters().getNodeName());
    }

}
