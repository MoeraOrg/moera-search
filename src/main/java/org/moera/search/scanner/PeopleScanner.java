package org.moera.search.scanner;

import java.util.UUID;
import jakarta.inject.Inject;

import org.moera.search.data.Database;
import org.moera.search.data.NodeRepository;
import org.moera.search.global.RequestCounter;
import org.moera.search.job.Jobs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PeopleScanner {

    private static final Logger log = LoggerFactory.getLogger(PeopleScanner.class);

    private static final int MAX_JOBS = 200;

    @Inject
    private Jobs jobs;

    @Inject
    private RequestCounter requestCounter;

    @Inject
    private Database database;

    @Inject
    private NodeRepository nodeRepository;

    @Scheduled(fixedDelayString = "PT1M")
    public void scan() {
        if (!jobs.isReady()) {
            return;
        }

        try (var ignored = requestCounter.allot()) {
            try (var ignored2 = database.open()) {
                int runningCount = database.executeRead(() -> jobs.countRunning(PeopleScanJob.class));
                if (runningCount >= MAX_JOBS) {
                    return;
                }
                var names = database.executeRead(() -> nodeRepository.findNamesToScanPeople(MAX_JOBS - runningCount));
                for (var name : names) {
                    log.debug("Starting people scan for {}", name);
                    try {
                        UUID jobId = jobs.run(PeopleScanJob.class, new PeopleScanJob.Parameters(name));
                        if (jobId != null) {
                            database.executeWriteWithoutResult(() -> nodeRepository.assignScanPeopleJob(name, jobId));
                        }
                    } catch (Exception e) {
                        log.error("Error starting people scan job for {}", name, e);
                    }
                }
            }
        }
    }

}
