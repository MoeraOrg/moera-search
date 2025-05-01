package org.moera.search.data;

import java.util.Map;
import jakarta.inject.Inject;

import org.springframework.stereotype.Component;

@Component
public class CacheCommentDigestRepository {

    @Inject
    private Database database;

    public byte[] getDigest(String nodeName, String postingId, String commentId, String revisionId) {
        return database.tx().run(
            """
            OPTIONAL MATCH (ch:CacheCommentDigest {
                nodeName: $nodeName, postingId: $postingId, commentId: $commentId, revisionId: $revisionId
            })
            RETURN ch.digest AS digest
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId,
                "revisionId", revisionId
            )
        ).single().get("digest").asByteArray(null);
    }

    public void storeDigest(String nodeName, String postingId, String commentId, String revisionId, byte[] digest) {
        database.tx().run(
            """
            MERGE (ch:CacheCommentDigest {
                nodeName: $nodeName, postingId: $postingId, commentId: $commentId, revisionId: $revisionId
            })
                ON CREATE
                    SET ch.digest = $digest
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId,
                "revisionId", revisionId,
                "digest", digest
            )
        );
    }

}
