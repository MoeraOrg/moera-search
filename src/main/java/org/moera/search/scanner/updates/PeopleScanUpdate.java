package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class PeopleScanUpdate extends PendingUpdate<PeopleScanJob.Parameters> {

    public PeopleScanUpdate(String nodeName) {
        super(new PeopleScanJob.Parameters(nodeName));
    }

    @Override
    protected Class<? extends Job<PeopleScanJob.Parameters, ?>> getJobClass() {
        return PeopleScanJob.class;
    }

    @Override
    protected Class<PeopleScanJob.Parameters> getJobParametersClass() {
        return PeopleScanJob.Parameters.class;
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
