package org.moera.search.scanner;

import java.util.UUID;
import jakarta.inject.Inject;

import org.moera.search.Workload;
import org.moera.search.data.Database;
import org.moera.search.data.NodeRepository;
import org.moera.search.global.RequestCounter;
import org.moera.search.index.Index;
import org.moera.search.job.Jobs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TimelineScanner {

    private static final Logger log = LoggerFactory.getLogger(TimelineScanner.class);

    @Inject
    private Jobs jobs;

    @Inject
    private RequestCounter requestCounter;

    @Inject
    private Database database;

    @Inject
    private Index index;

    @Inject
    private NodeRepository nodeRepository;

    @Scheduled(fixedDelayString = Workload.TIMELINE_SCANNERS_START_PERIOD)
    public void scan() {
        if (!jobs.isReady() || !index.isReady()) {
            return;
        }

        try (var ignored = requestCounter.allot()) {
            try (var ignored2 = database.open()) {
                int runningCount = database.read(() -> jobs.countRunning(TimelineScanJob.class));
                if (runningCount >= Workload.TIMELINE_SCANNERS_MAX_JOBS) {
                    return;
                }
                var names = database.read(() ->
                    nodeRepository.findNamesToScanTimeline(Workload.TIMELINE_SCANNERS_MAX_JOBS - runningCount)
                );
                for (var name : names) {
                    log.debug("Starting timeline scan for {}", name);
                    try {
                        UUID jobId = jobs.run(TimelineScanJob.class, new TimelineScanJob.Parameters(name));
                        if (jobId != null) {
                            database.writeNoResult(() -> nodeRepository.assignScanTimelineJob(name, jobId));
                        }
                    } catch (Exception e) {
                        log.error("Error starting timeline scan job for {}", name, e);
                    }
                }
            }
        }
    }

}
