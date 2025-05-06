package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class SheriffOrderUpdate extends PendingUpdate<SheriffOrderJob.Parameters> {

    public SheriffOrderUpdate() {
    }

    public SheriffOrderUpdate(
        boolean delete, String ownerName, String nodeName, String postingId, String commentId, String sheriffName
    ) {
        super(new SheriffOrderJob.Parameters(delete, ownerName, nodeName, postingId, commentId, sheriffName));
    }

    @Override
    protected Class<? extends Job<SheriffOrderJob.Parameters, ?>> getJobClass() {
        return SheriffOrderJob.class;
    }

    @Override
    protected Class<SheriffOrderJob.Parameters> getJobParametersClass() {
        return SheriffOrderJob.Parameters.class;
    }

    @Override
    public List<String> waitJobKeys() {
        return List.of(JobKeys.sheriff(getJobParameters().getSheriffName()));
    }

    @Override
    public String jobKey() {
        return JobKeys.sheriff(getJobParameters().getSheriffName());
    }

}
