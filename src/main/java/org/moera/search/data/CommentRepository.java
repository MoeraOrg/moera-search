package org.moera.search.data;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.types.CommentInfo;
import org.moera.lib.node.types.CommentOperations;
import org.moera.lib.node.types.body.Body;
import org.moera.lib.node.types.principal.Principal;
import org.moera.search.api.model.SearchRepliedToUtil;
import org.moera.search.util.BodyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CommentRepository {

    private static final Logger log = LoggerFactory.getLogger(CommentRepository.class);

    @Inject
    private Database database;

    @Inject
    private ObjectMapper objectMapper;

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
        if (info.getRepliedTo() != null) {
            try {
                String repliedTo = objectMapper.writeValueAsString(SearchRepliedToUtil.build(info.getRepliedTo()));
                args.put("repliedTo", repliedTo);
            } catch (JsonProcessingException e) {
                log.error("Cannot convert repliedTo to JSON", e);
            }
        } else {
            args.put("repliedTo", null);
        }
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
        args.put("viewPrincipal", CommentOperations.getView(info.getOperations(), Principal.PUBLIC).getValue());
        args.put("now", Instant.now().toEpochMilli());

        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(c:Comment {id: $commentId})
            SET c.revisionId = $revisionId,
                c.ownerFullName = $ownerFullName,
                c.heading = $heading,
                c.repliedTo = $repliedTo,
                c.bodyPreview = $bodyPreview,
                c.imageCount = $imageCount,
                c.videoPresent = $videoPresent,
                c.createdAt = $createdAt,
                c.editedAt = $editedAt,
                c.viewPrincipal = $viewPrincipal,
                c.scan = true,
                c.scannedAt = $now
            """,
            args
        );
    }

    public void setHeading(String nodeName, String postingId, String commentId, String heading) {
        var args = new HashMap<String, Object>();
        args.put("nodeName", nodeName);
        args.put("postingId", postingId);
        args.put("commentId", commentId);
        args.put("heading", heading);

        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(c:Comment {id: $commentId})
            SET c.heading = $heading
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

    public EntryRevision getRevision(String nodeName, String postingId, String commentId) {
        var r = database.tx().run(
            """
            OPTIONAL MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                           <-[:UNDER]-(c:Comment {id: $commentId})
            RETURN c.revisionId AS revisionId, c.viewPrincipal AS viewPrincipal
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId
            )
        ).single();

        return new EntryRevision(
            r.get("revisionId").asString(null),
            r.get("viewPrincipal").asString(Principal.PUBLIC.getValue())
        );
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

    public void sheriffMark(String sheriffName, String nodeName, String postingId, String commentId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(c:Comment {id: $commentId})
            WHERE c.sheriffMarks IS NULL OR NOT ($sheriffName IN c.sheriffMarks)
            SET c.sheriffMarks = CASE
                WHEN c.sheriffMarks IS NULL THEN [$sheriffName]
                ELSE c.sheriffMarks + [$sheriffName]
            END
            """,
            Map.of(
                "sheriffName", sheriffName,
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId
            )
        );
    }

    public void sheriffUnmark(String sheriffName, String nodeName, String postingId, String commentId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(c:Comment {id: $commentId})
            WHERE c.sheriffMarks IS NOT NULL AND $sheriffName IN c.sheriffMarks
            SET c.sheriffMarks = [mark IN c.sheriffMarks WHERE mark <> $sheriffName]
            """,
            Map.of(
                "sheriffName", sheriffName,
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId
            )
        );
    }

    public void addMediaPreview(
        String nodeName, String postingId, String commentId, String mediaId, String mediaFileId
    ) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(c:Comment {id: $commentId}),
                  (mf:MediaFile {id: $mediaFileId})
            CREATE (c)-[:MEDIA_PREVIEW {mediaId: $mediaId}]->(mf)
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId,
                "mediaId", mediaId,
                "mediaFileId", mediaFileId
            )
        );
    }

    public void removeMediaPreview(String nodeName, String postingId, String commentId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(:Comment {id: $commentId})-[mp:MEDIA_PREVIEW]->()
            DELETE mp
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId
            )
        );
    }

    public String getMediaPreviewId(String nodeName, String postingId, String commentId) {
        return database.tx().run(
            """
            OPTIONAL MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                           <-[:UNDER]-(:Comment {id: $commentId})-[mp:MEDIA_PREVIEW]->()
            RETURN mp.mediaId AS mediaId
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId
            )
        ).single().get("mediaId").asString(null);
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
