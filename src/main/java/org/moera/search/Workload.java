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

    public static final String NAME_SUBSCRIBERS_START_PERIOD = "PT1M";
    public static final int NAME_SUBSCRIBERS_MAX_JOBS = 500;

    public static final String PEOPLE_SCANNERS_START_PERIOD = "PT1M";
    public static final int PEOPLE_SCANNERS_MAX_JOBS = 200;

}
