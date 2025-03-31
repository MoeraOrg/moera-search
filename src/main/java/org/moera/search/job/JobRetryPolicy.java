package org.moera.search.job;

import java.time.Duration;

public interface JobRetryPolicy {

    boolean tryAgain();

    Duration waitTime();

}
