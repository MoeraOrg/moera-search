package org.moera.search.data;

import java.util.List;
import java.util.Map;
import jakarta.inject.Inject;

import org.springframework.stereotype.Component;

@Component
public class UpgradeRepository {

    @Inject
    private Database database;

    public record PostingLocation(String nodeName, String postingId) {
    }

    public List<PostingLocation> findPostingsToRescan(int limit) {
        return database.tx().run(
            """
            MATCH (n:MoeraNode)<-[:SOURCE]-(p:Posting)<-[:RESCAN]-(:Upgrade)
            LIMIT $limit
            RETURN n.name AS nodeName, p.id AS postingId
            """,
            Map.of("limit", limit)
        ).list(r -> new PostingLocation(r.get("nodeName").asString(), r.get("postingId").asString()));
    }

    public void deletePostingRescan(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})<-[rs:RESCAN]-(:Upgrade)
            DELETE rs
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

    public record CommentLocation(String nodeName, String postingId, String commentId) {
    }

    public List<CommentLocation> findCommentsToRescan(int limit) {
        return database.tx().run(
            """
            MATCH (n:MoeraNode)<-[:SOURCE]-(p:Posting)<-[:UNDER]-(c:Comment)<-[:RESCAN]-(:Upgrade)
            LIMIT $limit
            RETURN n.name AS nodeName, p.id AS postingId, c.id AS commentId
            """,
            Map.of("limit", limit)
        ).list(r ->
            new CommentLocation(
                r.get("nodeName").asString(), r.get("postingId").asString(), r.get("commentId").asString()
            )
        );
    }

    public void deleteCommentRescan(String nodeName, String postingId, String commentId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(:Comment {id: $commentId})<-[rs:RESCAN]-(:Upgrade)
            DELETE rs
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId
            )
        );
    }

}
