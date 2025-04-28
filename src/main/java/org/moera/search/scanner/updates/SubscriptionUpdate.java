package org.moera.search.scanner.updates;

import java.util.List;

import org.moera.search.data.PendingUpdate;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class SubscriptionUpdate extends PendingUpdate<SubscriptionJob.Parameters> {

    public SubscriptionUpdate(boolean unsubscribe, String nodeName, String subscriptionName, String feedName) {
        super(new SubscriptionJob.Parameters(unsubscribe, nodeName, subscriptionName, feedName));
    }

    @Override
    protected Class<? extends Job<SubscriptionJob.Parameters, ?>> getJobClass() {
        return SubscriptionJob.class;
    }

    @Override
    protected Class<SubscriptionJob.Parameters> getJobParametersClass() {
        return SubscriptionJob.Parameters.class;
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
