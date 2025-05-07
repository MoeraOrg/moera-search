package org.moera.search.scanner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import jakarta.inject.Inject;

import org.moera.search.Workload;
import org.moera.search.data.Database;
import org.moera.search.data.Favor;
import org.moera.search.data.NodeRepository;
import org.moera.search.global.RequestCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

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

            database.writeNoResult(() -> nodeRepository.linkCloseNodes(name));
            while (true) {
                var closeNodes = database.read(() ->
                    nodeRepository.findCloseNodes(
                        name,
                        Workload.CLOSE_TO_UPDATE_MAX_PEERS,
                        Workload.CLOSE_TO_UPDATE_MAX_FAVORS
                    )
                );
                if (closeNodes.isEmpty()) {
                    break;
                }
                for (var closeNode : closeNodes) {
                    float baseDistance = closeNode.isFriend() ? .75f : (closeNode.isSubscribed() ? 1 : 2);
                    float distance = baseDistance - calcCloseness(closeNode.favors());
                    database.writeNoResult(() ->
                        nodeRepository.setDistance(name, closeNode.name(), distance)
                    );
                }
            }
            database.writeNoResult(() -> nodeRepository.closeToUpdated(name));
            log.info("Update of close-tos finished");
        }
    }

    private float calcCloseness(List<Favor> favors) {
        if (ObjectUtils.isEmpty(favors)) {
            return 0;
        }

        double closeness = 0;
        for (Favor favor : favors) {
            long hours = Instant.ofEpochMilli(favor.getCreatedAt()).until(Instant.now(), ChronoUnit.HOURS);
            if (hours <= 0) {
                continue;
            }
            double passed = hours / (double) favor.getDecayHours();
            closeness += favor.getValue() * (1f - passed * passed);
        }
        return (float) Math.max(Math.tanh(closeness / 100), 0);
    }

    @Scheduled(fixedDelayString = Workload.CLOSE_TO_CLEANUP_CHECK_PERIOD)
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
        var names = database.read(
            () -> nodeRepository.findNamesForCloseToCleanup(Workload.CLOSE_TO_CLEANUP_MAX_NODES)
        );

        for (var name : names) {
            log.info("Cleaning up close-tos for {}", name);
            database.writeNoResult(() -> {
                nodeRepository.cleanupCloseTo(name);
                nodeRepository.closeToCleanedUp(name);
            });
        }
        log.info("Cleanup of close-tos finished");
    }

}
