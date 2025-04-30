package org.moera.search.data;

import java.util.HashMap;
import java.util.Map;
import jakarta.inject.Inject;

import org.moera.lib.node.types.ReactionInfo;
import org.moera.lib.node.types.ReactionOperations;
import org.moera.lib.node.types.principal.Principal;
import org.springframework.stereotype.Component;

@Component
public class ReactionRepository {

    @Inject
    private Database database;

    public void createReaction(String nodeName, ReactionInfo reaction) {
        var args = new HashMap<String, Object>();
        args.put("nodeName", nodeName);
        args.put("postingId", reaction.getPostingId());
        if (reaction.getCommentId() != null) {
            args.put("commentId", reaction.getCommentId());
        }
        args.put("ownerName", reaction.getOwnerName());
        args.put("ownerFullName", reaction.getOwnerFullName());
        args.put("negative", reaction.getNegative() != null ? reaction.getNegative() : false);
        args.put("emoji", reaction.getEmoji());
        args.put("createdAt", reaction.getCreatedAt());
        args.put("viewPrincipal", ReactionOperations.getView(reaction.getOperations(), Principal.PUBLIC).getValue());

        if (reaction.getCommentId() == null) {
            database.tx().run(
                """
                MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId}),
                      (o:MoeraNode {name: $ownerName})
                MERGE (p)<-[:REACTS_TO]-(r:Reaction)-[:OWNER]->(o)
                SET r.ownerFullName = $ownerFullName,
                    r.negative = $negative,
                    r.emoji = $emoji,
                    r.createdAt = $createdAt,
                    r.viewPrincipal = $viewPrincipal
                """,
                args
            );
        } else {
            database.tx().run(
                """
                MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                      <-[:UNDER]-(c:Comment {id: $commentId}), (o:MoeraNode {name: $ownerName})
                MERGE (c)<-[:REACTS_TO]-(r:Reaction)-[:OWNER]->(o)
                SET r.ownerFullName = $ownerFullName,
                    r.negative = $negative,
                    r.emoji = $emoji,
                    r.createdAt = $createdAt,
                    r.viewPrincipal = $viewPrincipal
                """,
                args
            );
        }
    }

    public void deleteReaction(String nodeName, String postingId, String ownerName) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:REACTS_TO]-(r:Reaction)-[:OWNER]->(:MoeraNode {name: $ownerName})
            DETACH DELETE r
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "ownerName", ownerName
            )
        );
    }

    public void deleteReaction(String nodeName, String postingId, String commentId, String ownerName) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(:Comment {id: $commentId})
                  <-[:REACTS_TO]-(r:Reaction)-[:OWNER]->(:MoeraNode {name: $ownerName})
            DETACH DELETE r
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId,
                "ownerName", ownerName
            )
        );
    }

    public void deleteAllReactions(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})<-[:REACTS_TO]-(r:Reaction)
            DETACH DELETE r
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

    public void deleteAllReactionsInComments(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(:Comment)<-[:REACTS_TO]-(r:Reaction)
            DETACH DELETE r
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

    public void deleteAllReactions(String nodeName, String postingId, String commentId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(:Comment {id: $commentId})<-[:REACTS_TO]-(r:Reaction)
            DETACH DELETE r
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId
            )
        );
    }

    public void addAvatar(String nodeName, String postingId, String ownerName, String mediaFileId, String shape) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:REACTS_TO]-(r:Reaction)-[:OWNER]->(o:MoeraNode {name: $ownerName}),
                  (mf:MediaFile {id: $mediaFileId})
            CREATE (r)-[:AVATAR {shape: $shape}]->(mf)
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "ownerName", ownerName,
                "mediaFileId", mediaFileId,
                "shape", shape
            )
        );
    }

    public void addAvatar(
        String nodeName, String postingId, String commentId, String ownerName, String mediaFileId, String shape
    ) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(:Comment {id: $commentId})
                  <-[:REACTS_TO]-(r:Reaction)-[:OWNER]->(o:MoeraNode {name: $ownerName}),
                  (mf:MediaFile {id: $mediaFileId})
            CREATE (r)-[:AVATAR {shape: $shape}]->(mf)
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId,
                "ownerName", ownerName,
                "mediaFileId", mediaFileId,
                "shape", shape
            )
        );
    }

    public void removeAvatar(String nodeName, String postingId, String ownerName) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:REACTS_TO]-(r:Reaction)-[:OWNER]->(:MoeraNode {name: $ownerName}),
                  (r)-[a:AVATAR]->()
            DELETE a
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "ownerName", ownerName
            )
        );
    }

    public void removeAvatar(String nodeName, String postingId, String commentId, String ownerName) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
                  <-[:UNDER]-(:Comment {id: $commentId})
                  <-[:REACTS_TO]-(r:Reaction)-[:OWNER]->(:MoeraNode {name: $ownerName}),
                  (r)-[a:AVATAR]->()
            DELETE a
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId,
                "ownerName", ownerName
            )
        );
    }

}
