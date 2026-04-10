package org.moera.search.data;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.inject.Inject;

import org.moera.lib.node.types.PrivateMediaFileInfo;
import org.moera.search.util.MediaTextUtil;
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
        args.put("attachment", mediaInfo.getAttachment());
        args.put("title", mediaInfo.getTitle());
        args.put("textContent", mediaInfo.getTextContent());

        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            CREATE (p)
                   <-[:ATTACHED]-
                   (:Attachment {
                       mediaId: $mediaId,
                       mimeType: $mimeType,
                       size: $size,
                       attachment: $attachment,
                       title: $title,
                       textContent: $textContent
                   })
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
        args.put("attachment", mediaInfo.getAttachment());
        args.put("title", mediaInfo.getTitle());
        args.put("textContent", mediaInfo.getTextContent());

        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(c:Comment {id: $commentId})
            CREATE (c)
                   <-[:ATTACHED]-
                   (:Attachment {
                       mediaId: $mediaId,
                       mimeType: $mimeType,
                       size: $size,
                       attachment: $attachment,
                       title: $title,
                       textContent: $textContent
                   })
            """,
            args
        );
    }

    public void updateMediaText(String nodeName, String postingId, String mediaId, String title, String textContent) {
        var args = new HashMap<String, Object>();
        args.put("nodeName", nodeName);
        args.put("postingId", postingId);
        args.put("mediaId", mediaId);
        args.put("title", title);
        args.put("textContent", textContent);
        args.put("titleChanged", title != null);
        args.put("textContentChanged", textContent != null);

        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:ATTACHED]-(a:Attachment {mediaId: $mediaId})
            SET a.title = CASE WHEN $titleChanged THEN $title ELSE a.title END,
                a.textContent = CASE WHEN $textContentChanged THEN $textContent ELSE a.textContent END
            """,
            args
        );
    }

    public void updateMediaText(
        String nodeName, String postingId, String commentId, String mediaId, String title, String textContent
    ) {
        var args = new HashMap<String, Object>();
        args.put("nodeName", nodeName);
        args.put("postingId", postingId);
        args.put("commentId", commentId);
        args.put("mediaId", mediaId);
        args.put("title", title);
        args.put("textContent", textContent);
        args.put("titleChanged", title != null);
        args.put("textContentChanged", textContent != null);

        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(:Comment {id: $commentId})<-[:ATTACHED]-(a:Attachment {mediaId: $mediaId})
            SET a.title = CASE WHEN $titleChanged THEN $title ELSE a.title END,
                a.textContent = CASE WHEN $textContentChanged THEN $textContent ELSE a.textContent END
            """,
            args
        );
    }

    public String getMediaText(String nodeName, String postingId) {
        return database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})<-[:ATTACHED]-(a:Attachment)
            RETURN a.title AS title, a.textContent AS textContent
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        )
            .stream()
            .map(r -> MediaTextUtil.buildMediaText(
                r.get("title").asString(null),
                r.get("textContent").asString(null)
            ))
            .filter(text -> !ObjectUtils.isEmpty(text))
            .collect(Collectors.joining(" "));
    }

    public String getMediaText(String nodeName, String postingId, String commentId) {
        return database.tx().run(
                """
                MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                      <-[:UNDER]-(:Comment {id: $commentId})<-[:ATTACHED]-(a:Attachment)
                RETURN a.title AS title, a.textContent AS textContent
                """,
                Map.of(
                    "nodeName", nodeName,
                    "postingId", postingId,
                    "commentId", commentId
                )
            )
            .stream()
            .map(r -> MediaTextUtil.buildMediaText(
                r.get("title").asString(null),
                r.get("textContent").asString(null)
            ))
            .filter(text -> !ObjectUtils.isEmpty(text))
            .collect(Collectors.joining(" "));
    }

}
