package org.moera.search.scanner;

import java.util.concurrent.atomic.AtomicInteger;
import jakarta.inject.Inject;

import org.moera.lib.naming.MoeraNaming;
import org.moera.lib.naming.NodeName;
import org.moera.search.config.Config;
import org.moera.search.data.Database;
import org.moera.search.data.DatabaseInitializedEvent;
import org.moera.search.data.NodeRepository;
import org.moera.search.data.NamingServiceRepository;
import org.moera.search.global.RequestCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NamingServiceScanner {

    private static final Logger log = LoggerFactory.getLogger(NamingServiceScanner.class);

    private static final int PAGE_SIZE = 100;

    @Inject
    private Config config;

    @Inject
    private RequestCounter requestCounter;

    @Inject
    private Database database;

    @Inject
    private NamingServiceRepository namingServiceRepository;

    @Inject
    private NodeRepository nodeRepository;

    @EventListener(DatabaseInitializedEvent.class)
    @Scheduled(fixedDelayString = "PT6H")
    private void scan() {
        if (!database.isReady()) {
            return;
        }

        try (var ignored = requestCounter.allot()) {
            log.info("Scanning naming service");

            var total = new AtomicInteger(0);
            try (var ignored2 = database.open()) {
                database.executeWriteWithoutResult(() -> {
                    long scanTimestamp = namingServiceRepository.getScanTimestamp();
                    long lastTimestamp = scanTimestamp;
                    var naming = new MoeraNaming(config.getNamingServer());
                    int page = 0;
                    while (true) {
                        var names = naming.getAllNewer(scanTimestamp, page++, PAGE_SIZE);
                        if (names.isEmpty()) {
                            break;
                        }
                        for (var name : names) {
                            var nodeName = NodeName.toString(name.getName(), name.getGeneration());
                            if (nodeRepository.existsName(nodeName)) {
                                continue;
                            }
                            nodeRepository.createName(nodeName);
                            total.incrementAndGet();
                            lastTimestamp = Math.max(lastTimestamp, name.getCreated());
                        }
                    }
                    if (lastTimestamp > scanTimestamp) {
                        namingServiceRepository.updateScanTimestamp(lastTimestamp);
                    }
                });
            }

            log.info("{} new names added", total.get());
        }
    }

}
