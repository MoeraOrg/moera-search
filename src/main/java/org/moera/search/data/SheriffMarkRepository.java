package org.moera.search.data;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import jakarta.inject.Inject;

import org.neo4j.driver.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Component
public class SheriffMarkRepository {

    @Inject
    private Database database;

    public void markNode(String sheriffName, String nodeName) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $nodeName})
            WHERE n.sheriffMarks IS NULL OR NOT ($sheriffName IN n.sheriffMarks)
            SET n.sheriffMarks = CASE
                WHEN n.sheriffMarks IS NULL THEN [$sheriffName]
                ELSE n.sheriffMarks + [$sheriffName]
            END
            """,
            Map.of(
                "sheriffName", sheriffName,
                "nodeName", nodeName
            )
        );
    }

    public void unmarkNode(String sheriffName, String nodeName) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $nodeName})
            WHERE n.sheriffMarks IS NOT NULL AND $sheriffName IN n.sheriffMarks
            SET n.sheriffMarks = [mark IN n.sheriffMarks WHERE mark <> $sheriffName]
            """,
            Map.of(
                "sheriffName", sheriffName,
                "nodeName", nodeName
            )
        );
    }

    public boolean nodeMarked(String sheriffName, String nodeName) {
        return database.tx().run(
            """
            RETURN EXISTS {
                MATCH (n:MoeraNode {name: $nodeName})
                WHERE n.sheriffMarks IS NOT NULL AND $sheriffName IN n.sheriffMarks
            } AS marked
            """,
            Map.of(
                "sheriffName", sheriffName,
                "nodeName", nodeName
            )
        ).single().get("marked").asBoolean(false);
    }

    public void markEntriesByOwner(String sheriffName, String nodeName) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:OWNER]-(e:Entry)
            WHERE e.sheriffMarks IS NULL OR NOT ($sheriffName IN e.sheriffMarks)
            SET e.sheriffMarks = CASE
                WHEN e.sheriffMarks IS NULL THEN [$sheriffName]
                ELSE e.sheriffMarks + [$sheriffName]
            END
            """,
            Map.of(
                "sheriffName", sheriffName,
                "nodeName", nodeName
            )
        );
    }

    public void createPostingMark(String sheriffName, String nodeName, String postingId) {
        database.tx().run(
            """
            MERGE (sm:SheriffPostingMark {nodeName: $nodeName, postingId: $postingId})
            SET sm.sheriffs = CASE
                WHEN sm.sheriffs IS NULL THEN [$sheriffName]
                WHEN $sheriffName IN sm.sheriffs THEN sm.sheriffs
                ELSE sm.sheriffs + [$sheriffName]
            END
            """,
            Map.of(
                "sheriffName", sheriffName,
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

    public void deletePostingMark(String sheriffName, String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (sm:SheriffPostingMark {nodeName: $nodeName, postingId: $postingId})
            WHERE sm.sheriffs IS NOT NULL AND $sheriffName IN sm.sheriffs
            SET sm.sheriffs = [mark IN sm.sheriffs WHERE mark <> $sheriffName]
            """,
            Map.of(
                "sheriffName", sheriffName,
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

    public void markPosting(String sheriffName, String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            WHERE p.sheriffMarks IS NULL OR NOT ($sheriffName IN p.sheriffMarks)
            SET p.sheriffMarks = CASE
                WHEN p.sheriffMarks IS NULL THEN [$sheriffName]
                ELSE p.sheriffMarks + [$sheriffName]
            END
            """,
            Map.of(
                "sheriffName", sheriffName,
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

    public void unmarkPosting(String sheriffName, String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            WHERE p.sheriffMarks IS NOT NULL AND $sheriffName IN p.sheriffMarks
            SET p.sheriffMarks = [mark IN p.sheriffMarks WHERE mark <> $sheriffName]
            """,
            Map.of(
                "sheriffName", sheriffName,
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

    public void unmarkPostingsByOwner(String sheriffName, String nodeName) {
        database.tx().run(
            """
            MATCH (n:MoeraNode)<-[:SOURCE]-(p:Posting)-[:OWNER]->(:MoeraNode {name: $nodeName})
            WHERE p.sheriffMarks IS NOT NULL
                AND $sheriffName IN p.sheriffMarks
                AND NOT EXISTS(
                    (sm:SheriffPostingMark
                     WHERE sm.nodeName = n.name AND sm.postingId = p.id AND $sheriffName IN sm.sheriffs)
                )
            SET p.sheriffMarks = [mark IN p.sheriffMarks WHERE mark <> $sheriffName]
            """,
            Map.of(
                "sheriffName", sheriffName,
                "nodeName", nodeName
            )
        );
    }

    public void createCommentMark(String sheriffName, String nodeName, String postingId, String commentId) {
        database.tx().run(
            """
            MERGE (sm:SheriffCommentMark {nodeName: $nodeName, postingId: $postingId, commentId: $commentId})
            SET sm.sheriffs = CASE
                WHEN sm.sheriffs IS NULL THEN [$sheriffName]
                WHEN $sheriffName IN sm.sheriffs THEN sm.sheriffs
                ELSE sm.sheriffs + [$sheriffName]
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

    public void deleteCommentMark(String sheriffName, String nodeName, String postingId, String commentId) {
        database.tx().run(
            """
            MATCH (sm:SheriffCommentMark {nodeName: $nodeName, postingId: $postingId, commentId: $commentId})
            WHERE sm.sheriffs IS NOT NULL AND $sheriffName IN sm.sheriffs
            SET sm.sheriffs = [mark IN sm.sheriffs WHERE mark <> $sheriffName]
            """,
            Map.of(
                "sheriffName", sheriffName,
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId
            )
        );
    }

    public void markComment(String sheriffName, String nodeName, String postingId, String commentId) {
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

    public void unmarkComment(String sheriffName, String nodeName, String postingId, String commentId) {
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

    public void unmarkCommentsByOwner(String sheriffName, String nodeName) {
        database.tx().run(
            """
            MATCH (n:MoeraNode)<-[:SOURCE]-(p:Posting)<-[:UNDER]-(c:Comment)-[:OWNER]->(:MoeraNode {name: $nodeName})
            WHERE c.sheriffMarks IS NOT NULL
                AND $sheriffName IN c.sheriffMarks
                AND NOT EXISTS(
                    (sm:SheriffCommentMark
                     WHERE sm.nodeName = n.name AND sm.postingId = p.id AND sm.commentId = c.id
                           AND $sheriffName IN sm.sheriffs)
                )
            SET c.sheriffMarks = [mark IN c.sheriffMarks WHERE mark <> $sheriffName]
            """,
            Map.of(
                "sheriffName", sheriffName,
                "nodeName", nodeName
            )
        );
    }

    public Set<String> findMarksForPosting(String nodeName, String postingId) {
        var record = database.tx().run(
            """
            OPTIONAL MATCH (n:MoeraNode {name: $nodeName}),
                           (sm:SheriffPostingMark {nodeName: $nodeName, postingId: $postingId})
            LIMIT 1
            RETURN n.sheriffMarks AS nodeMarks, sm.sheriffs AS postingMarks
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        ).single();

        var nodeMarks = record.get("nodeMarks").asList(Value::asString, null);
        var postingMarks = record.get("postingMarks").asList(Value::asString, null);

        if (ObjectUtils.isEmpty(nodeMarks) && ObjectUtils.isEmpty(postingMarks)) {
            return null;
        }

        var marks = new HashSet<String>();
        if (!ObjectUtils.isEmpty(nodeMarks)) {
            marks.addAll(nodeMarks);
        }
        if (!ObjectUtils.isEmpty(postingMarks)) {
            marks.addAll(postingMarks);
        }

        return marks;
    }

    public Set<String> findMarksForComment(String nodeName, String postingId, String commentId) {
        var record = database.tx().run(
            """
            OPTIONAL MATCH (n:MoeraNode {name: $nodeName}),
                           (sm:SheriffCommentMark {nodeName: $nodeName, postingId: $postingId, commentId: $commentId})
            LIMIT 1
            RETURN n.sheriffMarks AS nodeMarks, sm.sheriffs AS commentMarks
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "commentId", commentId
            )
        ).single();

        var nodeMarks = record.get("nodeMarks").asList(Value::asString, null);
        var commentMarks = record.get("commentMarks").asList(Value::asString, null);

        if (ObjectUtils.isEmpty(nodeMarks) && ObjectUtils.isEmpty(commentMarks)) {
            return null;
        }

        var marks = new HashSet<String>();
        if (!ObjectUtils.isEmpty(nodeMarks)) {
            marks.addAll(nodeMarks);
        }
        if (!ObjectUtils.isEmpty(commentMarks)) {
            marks.addAll(commentMarks);
        }

        return marks;
    }

}
