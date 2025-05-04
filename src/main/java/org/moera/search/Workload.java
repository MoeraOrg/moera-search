package org.moera.search;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class Workload {

    public static final String NAMING_CACHE_PURGE_PERIOD = "PT1M";
    public static final Duration NAMING_CACHE_NORMAL_TTL = Duration.of(6, ChronoUnit.HOURS);
    public static final Duration NAMING_CACHE_ERROR_TTL = Duration.of(1, ChronoUnit.MINUTES);

    public static final String MEDIA_FILES_PURGE_PERIOD = "PT6H";
    public static final Duration MEDIA_FILES_TTL = Duration.of(3, ChronoUnit.HOURS);

    public static final String NAMING_SERVICE_SCAN_PERIOD = "PT6H";

    public static final String NAME_SCANNERS_START_PERIOD = "PT1M";
    public static final int NAME_SCANNERS_MAX_JOBS = 500;

    public static final String CLOSE_TO_CHECK_PERIOD = "PT15M";
    public static final Duration CLOSE_TO_UPDATE_PERIOD = Duration.of(6, ChronoUnit.HOURS);
    public static final int CLOSE_TO_UPDATE_MAX_NODES = 100;
    public static final String CLOSE_TO_CLEANUP_PERIOD = "P1D";

    public static final int UPDATE_QUEUE_JOB_START_PERIOD = 15000; // ms
    public static final int UPDATE_QUEUE_MAX_STARTED_JOBS = 50; // ms

    public static final String FAVORS_PURGE_PERIOD = "PT1H";

}
