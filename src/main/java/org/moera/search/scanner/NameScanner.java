package org.moera.search.scanner;

import java.util.UUID;
import jakarta.inject.Inject;

import org.moera.search.Workload;
import org.moera.search.data.Database;
import org.moera.search.data.NodeRepository;
import org.moera.search.global.RequestCounter;
import org.moera.search.job.Jobs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NameScanner {

    private static final Logger log = LoggerFactory.getLogger(NameScanner.class);

    @Inject
    private Jobs jobs;

    @Inject
    private RequestCounter requestCounter;

    @Inject
    private Database database;

    @Inject
    private NodeRepository nodeRepository;

    @Scheduled(fixedDelayString = Workload.NAME_SCANNERS_START_PERIOD)
    public void scan() {
        if (!jobs.isReady()) {
            return;
        }

        try (var ignored = requestCounter.allot()) {
            try (var ignored2 = database.open()) {
                int runningCount = database.read(() -> jobs.countRunning(NameScanJob.class));
                if (runningCount >= Workload.NAME_SCANNERS_MAX_JOBS) {
                    return;
                }
                var names = database.read(() ->
                    nodeRepository.findNamesToScan(Workload.NAME_SCANNERS_MAX_JOBS - runningCount)
                );
                for (var name : names) {
                    log.debug("Starting scanning of {}", name);
                    try {
                        UUID jobId = jobs.run(NameScanJob.class, new NameScanJob.Parameters(name));
                        if (jobId != null) {
                            database.writeNoResult(() -> nodeRepository.assignScanJob(name, jobId));
                        }
                    } catch (Exception e) {
                        log.error("Error starting scanning of {}", name, e);
                    }
                }
            }
        }
    }

}
