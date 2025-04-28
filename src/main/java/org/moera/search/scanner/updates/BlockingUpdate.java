package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.lib.node.types.BlockedOperation;
import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class BlockingUpdate extends PendingUpdate<BlockingJob.Parameters> {

    public BlockingUpdate() {
    }

    public BlockingUpdate(boolean unblocks, String nodeName, String blockedName, BlockedOperation operation) {
        super(new BlockingJob.Parameters(unblocks, nodeName, blockedName, operation));
    }

    @Override
    protected Class<? extends Job<BlockingJob.Parameters, ?>> getJobClass() {
        return BlockingJob.class;
    }

    @Override
    protected Class<BlockingJob.Parameters> getJobParametersClass() {
        return BlockingJob.Parameters.class;
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
