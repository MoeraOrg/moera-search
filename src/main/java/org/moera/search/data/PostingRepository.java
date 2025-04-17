package org.moera.search.data;

import java.time.Instant;
import java.util.Map;
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

    public void createPosting(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $nodeName})
            MERGE (n)<-[:SOURCE]-(:Posting {id: $postingId})
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
        String nodeName, String postingId, String receiverName, String storyId, long publishedAt
    ) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId}),
                  (r:MoeraNode {name: $receiverName})
            MERGE (r)-[pb:PUBLISHED {storyId: $storyId}]->(p)
                ON CREATE
                    SET pb.publishedAt = $publishedAt
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "receiverName", receiverName,
                "storyId", storyId,
                "publishedAt", publishedAt
            )
        );
    }

    public void fillPosting(String nodeName, String postingId, PostingInfo info) {
        Body bodyPreview = info.getBodyPreview() != null ? info.getBodyPreview() : info.getBody();
        String bodyPreviewEncoded = bodyPreview != null ? bodyPreview.getEncoded() : "";

        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            SET p.revisionId = $revisionId,
                p.ownerFullName = $ownerFullName,
                p.heading = $heading,
                p.bodyPreview = $bodyPreview,
                p.createdAt = $createdAt,
                p.editedAt = $editedAt
                p.viewPrincipal = $viewPrincipal
                p.scan = true,
                p.scannedAt = $now
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "revisionId", info.getRevisionId(),
                "ownerFullName", info.getOwnerFullName(),
                "heading", info.getHeading(),
                "bodyPreview", bodyPreviewEncoded,
                "createdAt", info.getCreatedAt(),
                "editedAt", info.getEditedAt(),
                "viewPrincipal", PostingOperations.getView(info.getOperations(), Principal.PUBLIC).getValue(),
                "now", Instant.now().toEpochMilli()
            )
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

}
