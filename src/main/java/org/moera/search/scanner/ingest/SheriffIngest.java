package org.moera.search.scanner.ingest;

import jakarta.inject.Inject;

import org.moera.search.api.model.ObjectNotFoundFailure;
import org.moera.search.data.Database;
import org.moera.search.data.NodeRepository;
import org.moera.search.scanner.UpdateQueue;
import org.moera.search.scanner.updates.SheriffScanUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Component
public class SheriffIngest {

    private static final Logger log = LoggerFactory.getLogger(SheriffIngest.class);

    @Inject
    private Database database;

    @Inject
    private NodeRepository nodeRepository;

    @Inject
    private UpdateQueue updateQueue;

    public void activate(String name) {
        if (ObjectUtils.isEmpty(name)) {
            return;
        }
        var exists = database.read(() -> nodeRepository.exists(name));
        if (!exists) {
            throw new ObjectNotFoundFailure("sheriff.not-found");
        }
        var scannedSheriff = database.read(() -> nodeRepository.isScannedSheriff(name));
        if (!scannedSheriff) {
            log.info("Sheriff {} is referred for the first time, starting scanning", name);
            updateQueue.offer(new SheriffScanUpdate(name));
        }
    }

}
