package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class SheriffScanUpdate extends PendingUpdate<SheriffScanJob.Parameters> {

    public SheriffScanUpdate() {
    }

    public SheriffScanUpdate(String nodeName) {
        super(new SheriffScanJob.Parameters(nodeName));
    }

    @Override
    protected Class<? extends Job<SheriffScanJob.Parameters, ?>> getJobClass() {
        return SheriffScanJob.class;
    }

    @Override
    protected Class<SheriffScanJob.Parameters> getJobParametersClass() {
        return SheriffScanJob.Parameters.class;
    }

    @Override
    public List<String> waitJobKeys() {
        return List.of(JobKeys.sheriff(getJobParameters().getNodeName()));
    }

    @Override
    public String jobKey() {
        return JobKeys.sheriff(getJobParameters().getNodeName());
    }

}
