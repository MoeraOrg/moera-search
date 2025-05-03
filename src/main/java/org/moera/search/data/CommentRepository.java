package org.moera.search.data;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.inject.Inject;

import org.moera.lib.node.types.CommentInfo;
import org.moera.lib.node.types.CommentOperations;
import org.moera.lib.node.types.body.Body;
import org.moera.lib.node.types.principal.Principal;
import org.springframework.stereotype.Component;

@Component
public class CommentRepository {

    @Inject
    private Database database;

    public boolean exists(String nodeName, String postingId, String commentId) {
        return database.tx().run(
            """
            RETURN EXISTS {
                MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(:Comment {id: $commentId})
            } AS e
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId
            )
        ).single().get("e").asBoolean();
    }

    public void createComment(String nodeName, String postingId, String commentId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            MERGE (p)<-[:UNDER]-(c:Comment:Entry {id: $commentId})
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId
            )
        );
    }

    public void assignCommentOwner(String nodeName, String postingId, String commentId, String ownerName) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(c:Comment {id: $commentId}),
                  (o:MoeraNode {name: $ownerName})
            MERGE (o)<-[:OWNER]-(c)
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId,
                "ownerName", ownerName
            )
        );
    }

    public void assignCommentRepliedTo(String nodeName, String postingId, String commentId, String repliedToId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
                  <-[:UNDER]-(c:Comment {id: $commentId}),
                  (p)<-[:UNDER]-(rt:Comment {id: $repliedToId})
            MERGE (c)-[:REPLIED_TO]->(rt)
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId,
                "repliedToId", repliedToId
            )
        );
    }

    public void fillComment(String nodeName, String postingId, String commentId, CommentInfo info) {
        var args = new HashMap<String, Object>();
        args.put("nodeName", nodeName);
        args.put("postingId", postingId);
        args.put("commentId", commentId);
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
        args.put("viewPrincipal", CommentOperations.getView(info.getOperations(), Principal.PUBLIC).getValue());
        args.put("now", Instant.now().toEpochMilli());

        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(c:Comment {id: $commentId})
            SET c.revisionId = $revisionId,
                c.ownerFullName = $ownerFullName,
                c.heading = $heading,
                c.bodyPreview = $bodyPreview,
                c.createdAt = $createdAt,
                c.editedAt = $editedAt,
                c.viewPrincipal = $viewPrincipal,
                c.scan = true,
                c.scannedAt = $now
            """,
            args
        );
    }

    public void addAvatar(String nodeName, String postingId, String commentId, String mediaFileId, String shape) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(c:Comment {id: $commentId}),
                  (mf:MediaFile {id: $mediaFileId})
            CREATE (c)-[:AVATAR {shape: $shape}]->(mf)
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId,
                "mediaFileId", mediaFileId,
                "shape", shape
            )
        );
    }

    public void removeAvatar(String nodeName, String postingId, String commentId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(:Comment {id: $commentId})-[a:AVATAR]->()
            DELETE a
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId
            )
        );
    }

    public String getRevisionId(String nodeName, String postingId, String commentId) {
        return database.tx().run(
            """
            OPTIONAL MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                           <-[:UNDER]-(c:Comment {id: $commentId})
            RETURN c.revisionId AS id
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId
            )
        ).single().get("id").asString(null);
    }

    public String getDocumentId(String nodeName, String postingId, String commentId) {
        return database.tx().run(
            """
            OPTIONAL MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                           <-[:UNDER]-(c:Comment {id: $commentId})
            RETURN c.documentId AS id
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId
            )
        ).single().get("id").asString(null);
    }

    public void setDocumentId(String nodeName, String postingId, String commentId, String documentId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(c:Comment {id: $commentId})
            SET c.documentId = $documentId
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId,
                "documentId", documentId
            )
        );
    }

    public List<String> getAllDocumentIds(String nodeName, String postingId) {
        return database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})<-[:UNDER]-(c:Comment)
            RETURN c.documentId AS id
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        ).list(r -> r.get("id").asString(null));
    }

    public void scanSucceeded(String nodeName, String postingId, String commentId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(c:Comment {id: $commentId})
            SET c.scan = true, c.scannedAt = $now
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public void scanFailed(String nodeName, String postingId, String commentId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(c:Comment {id: $commentId})
            SET c.scan = false, c.scannedAt = $now
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public void deleteComment(String nodeName, String postingId, String commentId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(c:Comment {id: $commentId})
            DETACH DELETE c
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId
            )
        );
    }

    public void deleteAllComments(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})<-[:UNDER]-(c:Comment)
            DETACH DELETE c
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

    public boolean isReactionsScanned(String nodeName, String postingId, String commentId) {
        return database.tx().run(
            """
            OPTIONAL MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                           <-[:UNDER]-(c:Comment {id: $commentId})
            RETURN c.scanReactions IS NOT NULL AS scanned
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId
            )
        ).single().get("scanned").asBoolean();
    }

    public void scanReactionsSucceeded(String nodeName, String postingId, String commentId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(c:Comment {id: $commentId})
            SET c.scanReactions = true, c.reactionsScannedAt = $now
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public void scanReactionsFailed(String nodeName, String postingId, String commentId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(c:Comment {id: $commentId})
            SET c.scanReactions = false, c.reactionsScannedAt = $now
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

}
