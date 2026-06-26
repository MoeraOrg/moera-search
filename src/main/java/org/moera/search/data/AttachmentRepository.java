package org.moera.search.data;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.inject.Inject;

import org.moera.lib.node.types.MediaAttachment;
import org.moera.search.util.MediaAttachmentUtil;
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

    public void attach(String nodeName, String postingId, MediaAttachment attachment) {
        var args = new HashMap<String, Object>();
        args.put("nodeName", nodeName);
        args.put("postingId", postingId);
        args.put("mediaNodeName", MediaAttachmentUtil.nodeName(attachment));
        args.put("mediaId", MediaAttachmentUtil.mediaId(attachment));
        args.put("mimeType", MediaAttachmentUtil.mimeType(attachment));
        args.put("size", MediaAttachmentUtil.size(attachment));
        args.put("attachment", MediaAttachmentUtil.attachment(attachment));
        args.put("title", MediaAttachmentUtil.title(attachment));
        args.put("textContent", MediaAttachmentUtil.textContent(attachment));

        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            CREATE (p)
                   <-[:ATTACHED]-
                   (:Attachment {
                       mediaNodeName: $mediaNodeName,
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

    public void attach(String nodeName, String postingId, String commentId, MediaAttachment attachment) {
        var args = new HashMap<String, Object>();
        args.put("nodeName", nodeName);
        args.put("postingId", postingId);
        args.put("commentId", commentId);
        args.put("mediaNodeName", MediaAttachmentUtil.nodeName(attachment));
        args.put("mediaId", MediaAttachmentUtil.mediaId(attachment));
        args.put("mimeType", MediaAttachmentUtil.mimeType(attachment));
        args.put("size", MediaAttachmentUtil.size(attachment));
        args.put("attachment", MediaAttachmentUtil.attachment(attachment));
        args.put("title", MediaAttachmentUtil.title(attachment));
        args.put("textContent", MediaAttachmentUtil.textContent(attachment));

        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(c:Comment {id: $commentId})
            CREATE (c)
                   <-[:ATTACHED]-
                   (:Attachment {
                       mediaNodeName: $mediaNodeName,
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

    public void updateMediaLocation(
        String nodeName, String postingId, String remoteMediaNodeName, String remoteMediaId, String mediaId
    ) {
        var args = new HashMap<String, Object>();
        args.put("nodeName", nodeName);
        args.put("postingId", postingId);
        args.put("remoteMediaNodeName", remoteMediaNodeName);
        args.put("remoteMediaId", remoteMediaId);
        args.put("mediaId", mediaId);

        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})<-[:ATTACHED]-(a:Attachment)
            WHERE a.mediaNodeName = $remoteMediaNodeName AND a.mediaId = $remoteMediaId
            SET a.mediaNodeName = null, a.mediaId = $mediaId
            """,
            args
        );
    }

    public void updateMediaLocation(
        String nodeName, String postingId, String commentId, String remoteMediaNodeName, String remoteMediaId,
        String mediaId
    ) {
        var args = new HashMap<String, Object>();
        args.put("nodeName", nodeName);
        args.put("postingId", postingId);
        args.put("commentId", commentId);
        args.put("remoteMediaNodeName", remoteMediaNodeName);
        args.put("remoteMediaId", remoteMediaId);
        args.put("mediaId", mediaId);

        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(:Comment {id: $commentId})<-[:ATTACHED]-(a:Attachment)
            WHERE a.mediaNodeName = $remoteMediaNodeName AND a.mediaId = $remoteMediaId
            SET a.mediaNodeName = null, a.mediaId = $mediaId
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
            WHERE a.mediaNodeName IS NULL OR a.mediaNodeName = $nodeName
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
            WHERE a.mediaNodeName IS NULL OR a.mediaNodeName = $nodeName
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
