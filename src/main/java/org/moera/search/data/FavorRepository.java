package org.moera.search.data;

import java.time.Instant;
import java.util.Map;
import jakarta.inject.Inject;

import org.springframework.stereotype.Component;

@Component
public class FavorRepository {

    @Inject
    private Database database;

    public void createPublicationFavors(
        String nodeName, String postingId, String publisherName, String storyId, long createdAt, long deadline
    ) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})-[:OWNER]->(po:MoeraNode),
                  (p)<-[:CONTAINS]-(pb:Publication {feedName: 'timeline', storyId: $storyId})
                  -[:PUBLISHED_IN]->(pbo:MoeraNode {name: $publisherName})
            WHERE pbo <> po
            CREATE (f:Favor)
            CREATE (f)-[:DONE_TO]->(po)
            CREATE (f)-[:DONE_BY]->(pbo)
            CREATE (f)-[:CAUSED_BY]->(pb)
            SET f.value = $value,
                f.decayHours = $decayHours,
                f.createdAt = $createdAt,
                f.deadline = $deadline
            WITH p, pbo, pb
            CREATE (f:Favor)
            CREATE (f)-[:DONE_TO]->(p)
            CREATE (f)-[:DONE_BY]->(pbo)
            CREATE (f)-[:CAUSED_BY]->(pb)
            SET f.value = $value,
                f.decayHours = $decayHours,
                f.createdAt = $createdAt,
                f.deadline = $deadline
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "publisherName", publisherName,
                "storyId", storyId,
                "value", FavorType.PUBLICATION.getValue(),
                "decayHours", FavorType.PUBLICATION.getDecayHours(),
                "createdAt", createdAt,
                "deadline", deadline
            )
        );
    }

    public void deletePublicationFavors(String nodeName, String postingId, String publisherName) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:CONTAINS]-(pb:Publication)-[:PUBLISHED_IN]->(:MoeraNode {name: $publisherName}),
                  (pb)<-[:CAUSED_BY]-(f:Favor)
            DETACH DELETE f
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "publisherName", publisherName
            )
        );
    }

    public void deleteAllPublicationFavors(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:CONTAINS]-(:Publication)<-[:CAUSED_BY]-(f:Favor)
            DETACH DELETE f
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

    public void createCommentFavors(
        String nodeName, String postingId, String commentId, long createdAt, long deadline
    ) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
                  <-[:UNDER]-(c:Comment {id: $commentId})-[:OWNER]->(co:MoeraNode),
                  (p)-[:OWNER]->(po:MoeraNode)
            WHERE po <> co
            CREATE (f:Favor)
            CREATE (f)-[:DONE_TO]->(po)
            CREATE (f)-[:DONE_BY]->(co)
            CREATE (f)-[:CAUSED_BY]->(c)
            SET f.value = $value,
                f.decayHours = $decayHours,
                f.createdAt = $createdAt,
                f.deadline = $deadline
            WITH p, co, c
            CREATE (f:Favor)
            CREATE (f)-[:DONE_TO]->(p)
            CREATE (f)-[:DONE_BY]->(co)
            CREATE (f)-[:CAUSED_BY]->(c)
            SET f.value = $value,
                f.decayHours = $decayHours,
                f.createdAt = $createdAt,
                f.deadline = $deadline
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId,
                "value", FavorType.COMMENT.getValue(),
                "decayHours", FavorType.COMMENT.getDecayHours(),
                "createdAt", createdAt,
                "deadline", deadline
            )
        );
    }

    public void createRepliedToFavor(
        String nodeName, String postingId, String commentId, long createdAt, long deadline
    ) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(c:Comment {id: $commentId})-[:OWNER]->(co:MoeraNode),
                  (c)-[:REPLIED_TO]->(:Comment)-[:OWNER]->(rto:MoeraNode)
            WHERE rto <> co
            CREATE (f:Favor)
            CREATE (f)-[:DONE_TO]->(rto)
            CREATE (f)-[:DONE_BY]->(co)
            CREATE (f)-[:CAUSED_BY]->(c)
            SET f.value = $value,
                f.decayHours = $decayHours,
                f.createdAt = $createdAt,
                f.deadline = $deadline
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId,
                "value", FavorType.REPLY_TO_COMMENT.getValue(),
                "decayHours", FavorType.REPLY_TO_COMMENT.getDecayHours(),
                "createdAt", createdAt,
                "deadline", deadline
            )
        );
    }

    public void deleteCommentFavors(String nodeName, String postingId, String commentId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(:Comment {id: $commentId})<-[:CAUSED_BY]-(f:Favor)
            DETACH DELETE f
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId
            )
        );
    }

    public void deleteAllCommentFavors(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(:Comment)<-[:CAUSED_BY]-(f:Favor)
            DETACH DELETE f
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

    public void createPostingReactionFavors(
        String nodeName, String postingId, String reactionOwnerName, long createdAt, long deadline
    ) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
                  <-[:REACTS_TO]-(r:Reaction)-[:OWNER]->(ro:MoeraNode {name: $reactionOwnerName}),
                  (p)-[:OWNER]->(po:MoeraNode)
            WHERE po <> ro
            CREATE (f:Favor)
            CREATE (f)-[:DONE_TO]->(po)
            CREATE (f)-[:DONE_BY]->(ro)
            CREATE (f)-[:CAUSED_BY]->(r)
            SET f.value = $value,
                f.decayHours = $decayHours,
                f.createdAt = $createdAt,
                f.deadline = $deadline
            WITH p, ro, r
            CREATE (f:Favor)
            CREATE (f)-[:DONE_TO]->(p)
            CREATE (f)-[:DONE_BY]->(ro)
            CREATE (f)-[:CAUSED_BY]->(r)
            SET f.value = $value,
                f.decayHours = $decayHours,
                f.createdAt = $createdAt,
                f.deadline = $deadline
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "reactionOwnerName", reactionOwnerName,
                "value", FavorType.LIKE_POST.getValue(),
                "decayHours", FavorType.LIKE_POST.getDecayHours(),
                "createdAt", createdAt,
                "deadline", deadline
            )
        );
    }

    public void deletePostingReaction(String nodeName, String postingId, String reactionOwnerName) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:REACTS_TO]-(r:Reaction)-[:OWNER]->(:MoeraNode {name: $reactionOwnerName}),
                  (f:Favor)-[:CAUSED_BY]->(r)
            DETACH DELETE f
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "reactionOwnerName", reactionOwnerName
            )
        );
    }

    public void deleteAllPostingReactions(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:REACTS_TO]-(:Reaction)<-[:CAUSED_BY]-(f:Favor)
            DETACH DELETE f
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

    public void createCommentReactionFavors(
        String nodeName, String postingId, String commentId, String reactionOwnerName, long createdAt, long deadline
    ) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(c:Comment {id: $commentId})
                  <-[:REACTS_TO]-(r:Reaction)-[:OWNER]->(ro:MoeraNode {name: $reactionOwnerName}),
                  (c)-[:OWNER]->(co:MoeraNode)
            WHERE co <> ro
            CREATE (f:Favor)
            CREATE (f)-[:DONE_TO]->(co)
            CREATE (f)-[:DONE_BY]->(ro)
            CREATE (f)-[:CAUSED_BY]->(r)
            SET f.value = $value,
                f.decayHours = $decayHours,
                f.createdAt = $createdAt,
                f.deadline = $deadline
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId,
                "reactionOwnerName", reactionOwnerName,
                "value", FavorType.LIKE_COMMENT.getValue(),
                "decayHours", FavorType.LIKE_COMMENT.getDecayHours(),
                "createdAt", createdAt,
                "deadline", deadline
            )
        );
    }

    public void deleteCommentReaction(String nodeName, String postingId, String commentId, String reactionOwnerName) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(:Comment {id: $commentId})
                  <-[:REACTS_TO]-(r:Reaction)-[:OWNER]->(:MoeraNode {name: $reactionOwnerName}),
                  (f:Favor)-[:CAUSED_BY]->(r)
            DETACH DELETE f
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId,
                "reactionOwnerName", reactionOwnerName
            )
        );
    }

    public void deleteAllCommentReactions(String nodeName, String postingId, String commentId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(:Comment {id: $commentId})<-[:REACTS_TO]-(:Reaction)<-[:CAUSED_BY]-(f:Favor)
            DETACH DELETE f
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId
            )
        );
    }

    public void deleteAllReactionsInComments(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(:Comment)<-[:REACTS_TO]-(:Reaction)<-[:CAUSED_BY]-(f:Favor)
            DETACH DELETE f
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

    public void purgeExpired() {
        database.tx().run(
            """
            MATCH (f:Favor)
            WHERE f.deadline < $now
            DETACH DELETE f
            """,
            Map.of("now", Instant.now().toEpochMilli())
        );
    }

}
