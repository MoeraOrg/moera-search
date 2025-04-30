package org.moera.search.data;

import java.util.Map;
import jakarta.inject.Inject;

import org.springframework.stereotype.Component;

@Component
public class CachePostingDigestRepository {

    @Inject
    private Database database;

    public byte[] getDigest(String nodeName, String postingId, String revisionId) {
        return database.tx().run(
            """
            OPTIONAL MATCH (ch:CachePostingDigest {nodeName: $nodeName, postingId: $postingId, revisionId: $revisionId})
            RETURN ch.digest AS digest
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "revisionId", revisionId
            )
        ).single().get("digest").asByteArray(null);
    }

    public void storeDigest(String nodeName, String postingId, String revisionId, byte[] digest) {
        database.tx().run(
            """
            MERGE (ch:CachePostingDigest {nodeName: $nodeName, postingId: $postingId, revisionId: $revisionId})
                ON CREATE
                    SET ch.digest = $digest
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "revisionId", revisionId,
                "digest", digest
            )
        );
    }

}
