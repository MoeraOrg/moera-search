package org.moera.search.data;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import jakarta.inject.Inject;

import org.moera.lib.node.types.PostingInfo;
import org.moera.lib.node.types.PostingOperations;
import org.moera.lib.node.types.body.Body;
import org.moera.lib.node.types.principal.Principal;
import org.moera.search.util.BodyUtil;
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
            MERGE (n)<-[:SOURCE]-(p:Posting:Entry {id: $postingId})
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
        var counts = BodyUtil.countBodyMedia(info.getBody(), info.getMedia());
        args.put("imageCount", counts.imageCount());
        args.put("videoPresent", counts.videoPresent());
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
                p.imageCount = $imageCount,
                p.videoPresent = $videoPresent,
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

    public record PostingRevision(String revisionId, String viewPrincipal) {
    }

    public PostingRevision getRevision(String nodeName, String postingId) {
        var r = database.tx().run(
            """
            OPTIONAL MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            RETURN p.revisionId AS revisionId, p.viewPrincipal AS viewPrincipal
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        ).single();

        return new PostingRevision(
            r.get("revisionId").asString(null),
            r.get("viewPrincipal").asString(Principal.PUBLIC.getValue())
        );
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

    public void sheriffMark(String sheriffName, String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            WHERE p.sheriffMarks IS NULL OR NOT ($sheriffName IN p.sheriffMarks)
            SET p.sheriffMarks = CASE
                WHEN p.sheriffMarks IS NULL THEN [$sheriffName]
                ELSE p.sheriffMarks + [$sheriffName]
            END
            """,
            Map.of(
                "sheriffName", sheriffName,
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

    public void sheriffUnmark(String sheriffName, String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            WHERE p.sheriffMarks IS NOT NULL AND $sheriffName IN p.sheriffMarks
            SET p.sheriffMarks = [mark IN p.sheriffMarks WHERE mark <> $sheriffName]
            """,
            Map.of(
                "sheriffName", sheriffName,
                "nodeName", nodeName,
                "postingId", postingId
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

    public boolean isReactionsScanned(String nodeName, String postingId) {
        return database.tx().run(
            """
            OPTIONAL MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            RETURN p.scanReactions IS NOT NULL AS scanned
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        ).single().get("scanned").asBoolean();
    }

    public void scanReactionsSucceeded(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            SET p.scanReactions = true, p.reactionsScannedAt = $now
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public void scanReactionsFailed(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            SET p.scanReactions = false, p.reactionsScannedAt = $now
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

}
