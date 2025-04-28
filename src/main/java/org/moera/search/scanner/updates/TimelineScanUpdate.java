package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class TimelineScanUpdate extends PendingUpdate<TimelineScanJob.Parameters> {

    public TimelineScanUpdate(String nodeName) {
        super(new TimelineScanJob.Parameters(nodeName));
    }

    @Override
    protected Class<? extends Job<TimelineScanJob.Parameters, ?>> getJobClass() {
        return TimelineScanJob.class;
    }

    @Override
    protected Class<TimelineScanJob.Parameters> getJobParametersClass() {
        return TimelineScanJob.Parameters.class;
    }

    @Override
    public List<String> waitJobKeys() {
        return List.of(JobKeys.anyContent(getJobParameters().getNodeName()));
    }

    @Override
    public String jobKey() {
        return JobKeys.allContent(getJobParameters().getNodeName());
    }

}
