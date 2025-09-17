package org.moera.search.scanner;

import jakarta.inject.Inject;

import org.moera.search.Workload;
import org.moera.search.data.Database;
import org.moera.search.data.NodeRepository;
import org.moera.search.global.RequestCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ActivityUpdater {

    private static final Logger log = LoggerFactory.getLogger(ActivityUpdater.class);

    @Inject
    private RequestCounter requestCounter;

    @Inject
    private Database database;

    @Inject
    private NodeRepository nodeRepository;

    @Scheduled(fixedDelayString = Workload.ACTIVITY_CHECK_PERIOD)
    public void scheduledUpdate() {
        if (!database.isReady()) {
            return;
        }

        try (var ignored = requestCounter.allot()) {
            try (var ignored2 = database.open()) {
                log.info("Updating nodes' activity");
                database.writeNoResult(() -> nodeRepository.updateActivity(Workload.ACTIVITY_UPDATE_MAX_NODES));
            }
        }
    }

}
