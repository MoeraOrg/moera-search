package org.moera.search.job;

import java.time.Duration;

import org.apache.commons.lang3.NotImplementedException;

public class JobNoRetryPolicy implements JobRetryPolicy {

    @Override
    public boolean tryAgain() {
        return false;
    }

    @Override
    public Duration waitTime() {
        throw new NotImplementedException();
    }

}
