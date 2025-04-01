package org.moera.search.data;

import java.time.Instant;
import java.util.Map;
import jakarta.inject.Inject;

import org.springframework.stereotype.Component;

@Component
public class NamingServiceRepository {

    @Inject
    private Database database;

    public long getScanTimestamp() {
        return database.tx().run(
            """
            MATCH (ns:NamingService)
            RETURN ns.scanTimestamp AS scanTimestamp
            """
        ).single().get("scanTimestamp").asLong();
    }

    public void updateScanTimestamp(long timestamp) {
        database.tx().run(
            """
            MATCH (ns:NamingService)
            SET ns.scanTimestamp = $timestamp, ns.scannedAt = $now
            """,
            Map.of(
                "timestamp", timestamp,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

}
