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
        args.put("ownerName", reaction.getOwnerName());
        args.put("ownerFullName", reaction.getOwnerFullName());
        args.put("negative", reaction.getNegative() != null ? reaction.getNegative() : false);
        args.put("emoji", reaction.getEmoji());
        args.put("createdAt", reaction.getCreatedAt());
        args.put("viewPrincipal", ReactionOperations.getView(reaction.getOperations(), Principal.PUBLIC).getValue());

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

}
