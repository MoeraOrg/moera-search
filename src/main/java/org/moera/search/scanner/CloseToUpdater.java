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
public class CloseToUpdater {

    private static final Logger log = LoggerFactory.getLogger(CloseToUpdater.class);

    @Inject
    private RequestCounter requestCounter;

    @Inject
    private Database database;

    @Inject
    private NodeRepository nodeRepository;

    @Scheduled(fixedDelayString = Workload.CLOSE_TO_CHECK_PERIOD)
    public void scheduledUpdate() {
        if (!database.isReady()) {
            return;
        }

        try (var ignored = requestCounter.allot()) {
            try (var ignored2 = database.open()) {
                update();
            }
        }
    }

    public void update() {
        var names = database.read(
            () -> nodeRepository.findNamesForCloseToUpdate(Workload.CLOSE_TO_UPDATE_MAX_NODES)
        );

        for (var name : names) {
            log.info("Updating close-tos for {}", name);

            var closeNodes = database.read(() -> nodeRepository.findCloseNodes(name));
            for (var closeNode : closeNodes) {
                float distance = closeNode.isFriend() ? .75f : (closeNode.isSubscribed() ? 1 : 2);
                database.writeNoResult(() ->
                    nodeRepository.setDistance(name, closeNode.name(), distance)
                );
            }
            database.writeNoResult(() -> nodeRepository.closeToUpdated(name));
        }
    }

    @Scheduled(fixedDelayString = Workload.CLOSE_TO_CLEANUP_PERIOD)
    public void scheduledCleanup() {
        if (!database.isReady()) {
            return;
        }

        try (var ignored = requestCounter.allot()) {
            try (var ignored2 = database.open()) {
                cleanup();
            }
        }
    }

    public void cleanup() {
        log.info("Cleaning up close-tos");
        database.writeNoResult(() -> nodeRepository.cleanupCloseTo());
        log.info("Cleanup of close-tos finished");
    }

}
