package org.moera.search.data;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.inject.Inject;

import org.moera.lib.node.types.PostingInfo;
import org.moera.lib.node.types.PostingOperations;
import org.moera.lib.node.types.body.Body;
import org.moera.lib.node.types.principal.Principal;
import org.springframework.stereotype.Component;

@Component
public class PostingRepository {

    @Inject
    private Database database;

    public boolean exists(String nodeName, String postingId) {
        return database.tx().run(
            """
            RETURN EXISTS {
                MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
            } AS e
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        ).single().get("e").asBoolean();
    }

    public boolean createPosting(String nodeName, String postingId) {
        return database.tx().run(
            """
            MATCH (n:MoeraNode {name: $nodeName})
            MERGE (n)<-[:SOURCE]-(p:Posting {id: $postingId})
            RETURN p.scan IS NOT NULL AS scanned
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        ).single().get("scanned").asBoolean();
    }

    public void deletePosting(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            DETACH DELETE p
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

    public void assignPostingOwner(String nodeName, String postingId, String ownerName) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId}),
                  (o:MoeraNode {name: $ownerName})
            MERGE (o)<-[:OWNER]-(p)
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "ownerName", ownerName
            )
        );
    }

    public void addPublication(
        String nodeName, String postingId, String publisherName, String feedName, String storyId, long publishedAt
    ) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId}),
                  (r:MoeraNode {name: $publisherName})
            MERGE (r)-[pb:PUBLISHED {feedName: $feedName, storyId: $storyId}]->(p)
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
                  <-[pb:PUBLISHED]-(:MoeraNode {name: $publisherName})
            DELETE pb
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "publisherName", publisherName
            )
        );
    }

    public void fillPosting(String nodeName, String postingId, PostingInfo info) {
        var args = new HashMap<String, Object>();
        args.put("nodeName", nodeName);
        args.put("postingId", postingId);
        args.put("revisionId", info.getRevisionId());
        args.put("ownerFullName", info.getOwnerFullName());
        args.put("heading", info.getHeading());
        Body bodyPreview = info.getBodyPreview() != null && !info.getBodyPreview().getEncoded().equals(Body.EMPTY)
            ? info.getBodyPreview()
            : info.getBody();
        String bodyPreviewEncoded = bodyPreview != null ? bodyPreview.getEncoded() : "";
        args.put("bodyPreview", bodyPreviewEncoded);
        args.put("createdAt", info.getCreatedAt());
        args.put("editedAt", info.getEditedAt());
        args.put("viewPrincipal", PostingOperations.getView(info.getOperations(), Principal.PUBLIC).getValue());
        args.put("now", Instant.now().toEpochMilli());

        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            SET p.revisionId = $revisionId,
                p.ownerFullName = $ownerFullName,
                p.heading = $heading,
                p.bodyPreview = $bodyPreview,
                p.createdAt = $createdAt,
                p.editedAt = $editedAt,
                p.viewPrincipal = $viewPrincipal,
                p.scan = true,
                p.scannedAt = $now
            """,
            args
        );
    }

    public void addAvatar(String nodeName, String postingId, String mediaFileId, String shape) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId}),
                  (mf:MediaFile {id: $mediaFileId})
            CREATE (p)-[:AVATAR {shape: $shape}]->(mf)
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "mediaFileId", mediaFileId,
                "shape", shape
            )
        );
    }

    public void removeAvatar(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})-[a:AVATAR]->()
            DELETE a
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

    public String getRevisionId(String nodeName, String postingId) {
        return database.tx().run(
            """
            OPTIONAL MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            RETURN p.revisionId AS id
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        ).single().get("id").asString(null);
    }

    public String getDocumentId(String nodeName, String postingId) {
        return database.tx().run(
            """
            OPTIONAL MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            RETURN p.documentId AS id
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        ).single().get("id").asString(null);
    }

    public void setDocumentId(String nodeName, String postingId, String documentId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            SET p.documentId = $documentId
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "documentId", documentId
            )
        );
    }

    public List<String> getPublishers(String nodeName, String postingId) {
        return database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})<-[:PUBLISHED]-(n:MoeraNode)
            RETURN n.name AS name
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        ).stream().map(r -> r.get("name").asString()).toList();
    }

    public record PostingAtNode(String nodeName, String postingId) {
    }

    public List<PostingAtNode> findPostingsToScan(int limit) {
        return database.tx().run(
            """
            MATCH (n:MoeraNode)<-[:SOURCE]-(p:Posting)
            WHERE p.scan IS NULL AND NOT (p)<-[:SCANS_POSTING]-(:Job)
            LIMIT $limit
            RETURN n.name AS nodeName, p.id AS postingId
            """,
            Map.of("limit", limit)
        ).list(r -> new PostingAtNode(r.get("nodeName").asString(), r.get("postingId").asString()));
    }

    public void assignScanJob(String nodeName, String postingId, UUID jobId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId}), (j:Job {id: $jobId})
            MERGE (p)<-[:SCANS_POSTING]-(j)
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "jobId", jobId.toString()
            )
        );
    }

    public void scanSucceeded(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            SET p.scan = true, p.scannedAt = $now
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public void scanFailed(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            SET p.scan = false, p.scannedAt = $now
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

}
