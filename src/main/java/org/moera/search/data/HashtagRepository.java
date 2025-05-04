package org.moera.search.data;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import jakarta.inject.Inject;

import org.springframework.stereotype.Component;

@Component
public class HashtagRepository {

    @Inject
    private Database database;

    public void createHashtag(String name) {
        database.tx().run(
            """
            MERGE (h:Hashtag {name: $name})
                ON CREATE
                    SET h.createdAt = $now
            """,
            Map.of(
                "name", name,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public void mark(String nodeName, String postingId, List<String> hashtags) {
        database.tx().run(
            """
            UNWIND $hashtags AS hn
            MATCH (h:Hashtag {name: hn}), (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            MERGE (p)-[:MARKED_WITH]->(h)
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "hashtags", hashtags
            )
        );
    }

    public void mark(String nodeName, String postingId, String commentId, List<String> hashtags) {
        database.tx().run(
            """
            UNWIND $hashtags AS hn
            MATCH (h:Hashtag {name: hn}),
                  (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(c:Comment {id: $commentId})
            MERGE (c)-[:MARKED_WITH]->(h)
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId,
                "hashtags", hashtags
            )
        );
    }

    public void unmark(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})-[m:MARKED_WITH]->(:Hashtag)
            DELETE m
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

    public void unmark(String nodeName, String postingId, String commentId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(:Comment {id: $commentId})-[m:MARKED_WITH]->(:Hashtag)
            DELETE m
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId
            )
        );
    }

}
