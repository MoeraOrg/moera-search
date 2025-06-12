package org.moera.search.data;

import java.util.HashMap;
import java.util.Map;
import jakarta.inject.Inject;

import org.moera.lib.node.types.PrivateMediaFileInfo;
import org.springframework.stereotype.Component;

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

}
