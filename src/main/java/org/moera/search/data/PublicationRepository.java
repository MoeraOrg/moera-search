package org.moera.search.data;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.springframework.stereotype.Component;

@Component
public class PublicationRepository {

    @Inject
    private Database database;

    public void addPublication(
        String nodeName, String postingId, String publisherName, String feedName, String storyId, long publishedAt
    ) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId}),
                  (r:MoeraNode {name: $publisherName})
            MERGE (r)<-[:PUBLISHED_IN]-(pb:Publication {feedName: $feedName, storyId: $storyId})-[:CONTAINS]->(p)
                ON CREATE
                    SET pb.publishedAt = $publishedAt
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "publisherName", publisherName,
                "feedName", feedName,
                "storyId", storyId,
                "publishedAt", publishedAt
            )
        );
    }

    public void deletePublications(String nodeName, String postingId, String publisherName) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:CONTAINS]-(pb:Publication)-[:PUBLISHED_IN]->(:MoeraNode {name: $publisherName})
            DETACH DELETE pb
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "publisherName", publisherName
            )
        );
    }

    public void deleteAllPublications(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})<-[:CONTAINS]-(pb:Publication)
            DETACH DELETE pb
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

    public List<String> getPublishers(String nodeName, String postingId) {
        return database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:CONTAINS]-(:Publication)-[:PUBLISHED_IN]->(n:MoeraNode)
            RETURN n.name AS name
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        ).stream().map(r -> r.get("name").asString()).toList();
    }

}
