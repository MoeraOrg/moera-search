package org.moera.search.data;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.inject.Inject;

import org.moera.lib.node.types.PrivateMediaFileInfo;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Component
public class AttachmentRepository {

    @Inject
    private Database database;

    public void deleteAll(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})<-[:ATTACHED]-(a:Attachment)
            DETACH DELETE a
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

    public void deleteAll(String nodeName, String postingId, String commentId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(:Comment {id: $commentId})<-[:ATTACHED]-(a:Attachment)
            DETACH DELETE a
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId
            )
        );
    }

    public void attach(String nodeName, String postingId, PrivateMediaFileInfo mediaInfo) {
        var args = new HashMap<String, Object>();
        args.put("nodeName", nodeName);
        args.put("postingId", postingId);
        args.put("mediaId", mediaInfo.getId());
        args.put("mimeType", mediaInfo.getMimeType());
        args.put("size", mediaInfo.getSize());
        args.put("textContent", mediaInfo.getTextContent());

        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            CREATE (p)
                   <-[:ATTACHED]-
                   (:Attachment {mediaId: $mediaId, mimeType: $mimeType, size: $size, textContent: $textContent})
            """,
            args
        );
    }

    public void attach(String nodeName, String postingId, String commentId, PrivateMediaFileInfo mediaInfo) {
        var args = new HashMap<String, Object>();
        args.put("nodeName", nodeName);
        args.put("postingId", postingId);
        args.put("commentId", commentId);
        args.put("mediaId", mediaInfo.getId());
        args.put("mimeType", mediaInfo.getMimeType());
        args.put("size", mediaInfo.getSize());
        args.put("textContent", mediaInfo.getTextContent());

        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(c:Comment {id: $commentId})
            CREATE (c)
                   <-[:ATTACHED]-
                   (:Attachment {mediaId: $mediaId, mimeType: $mimeType, size: $size, textContent: $textContent})
            """,
            args
        );
    }

    public void setTextContent(String nodeName, String postingId, String mediaId, String textContent) {
        var args = new HashMap<String, Object>();
        args.put("nodeName", nodeName);
        args.put("postingId", postingId);
        args.put("mediaId", mediaId);
        args.put("textContent", textContent);

        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:ATTACHED]-(a:Attachment {mediaId: $mediaId})
            SET a.textContent = $textContent
            """,
            args
        );
    }

    public void setTextContent(
        String nodeName, String postingId, String commentId, String mediaId, String textContent
    ) {
        var args = new HashMap<String, Object>();
        args.put("nodeName", nodeName);
        args.put("postingId", postingId);
        args.put("commentId", commentId);
        args.put("mediaId", mediaId);
        args.put("textContent", textContent);

        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(:Comment {id: $commentId})<-[:ATTACHED]-(a:Attachment {mediaId: $mediaId})
            SET a.textContent = $textContent
            """,
            args
        );
    }

    public String getMediaText(String nodeName, String postingId) {
        return database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})<-[:ATTACHED]-(a:Attachment)
            WHERE a.textContent IS NOT NULL
            RETURN a.textContent AS text
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        )
            .stream()
            .map(r -> r.get("text").asString(null))
            .filter(tc -> !ObjectUtils.isEmpty(tc))
            .collect(Collectors.joining(" "));
    }

    public String getMediaText(String nodeName, String postingId, String commentId) {
        return database.tx().run(
                """
                MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                      <-[:UNDER]-(:Comment {id: $commentId})<-[:ATTACHED]-(a:Attachment)
                WHERE a.textContent IS NOT NULL
                RETURN a.textContent AS text
                """,
                Map.of(
                    "nodeName", nodeName,
                    "postingId", postingId,
                    "commentId", commentId
                )
            )
            .stream()
            .map(r -> r.get("text").asString(null))
            .filter(tc -> !ObjectUtils.isEmpty(tc))
            .collect(Collectors.joining(" "));
    }

}
