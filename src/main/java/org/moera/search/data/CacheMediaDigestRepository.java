package org.moera.search.data;

import java.util.Map;
import jakarta.inject.Inject;

import org.springframework.stereotype.Component;

@Component
public class CacheMediaDigestRepository {

    @Inject
    private Database database;

    public byte[] getDigest(String nodeName, String mediaId) {
        return database.tx().run(
            """
            OPTIONAL MATCH (ch:CacheMediaDigest {nodeName: $nodeName, mediaId: $mediaId})
            RETURN ch.digest AS digest
            """,
            Map.of(
                "nodeName", nodeName,
                "mediaId", mediaId
            )
        ).single().get("digest").asByteArray(null);
    }

    public void storeDigest(String nodeName, String mediaId, byte[] digest) {
        database.tx().run(
            """
            MERGE (ch:CacheMediaDigest {nodeName: $nodeName, mediaId: $mediaId})
                ON CREATE
                    SET ch.digest = $digest
            """,
            Map.of(
                "nodeName", nodeName,
                "mediaId", mediaId,
                "digest", digest
            )
        );
    }

}
