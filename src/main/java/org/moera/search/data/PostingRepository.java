package org.moera.search.data;

import java.time.Instant;
import java.util.HashMap;
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

    public void createPosting(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $nodeName})
            MERGE (n)<-[:SOURCE]-(p:Posting {id: $postingId})
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
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

    public boolean isCommentsScanned(String nodeName, String postingId) {
        return database.tx().run(
            """
            OPTIONAL MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            RETURN p.scanComments IS NOT NULL AS scanned
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        ).single().get("scanned").asBoolean();
    }

    public void scanCommentsSucceeded(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            SET p.scanComments = true, p.commentsScannedAt = $now
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public void scanCommentsFailed(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            SET p.scanComments = false, p.commentsScannedAt = $now
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

}
