package org.moera.search.data;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.inject.Inject;

import org.moera.lib.node.types.BlockedOperation;
import org.moera.lib.node.types.WhoAmI;
import org.moera.search.Workload;
import org.springframework.stereotype.Component;

@Component
public class NodeRepository {

    private static final int POSTING_ACTIVITY_DECAY_HOURS = 90 * 24;

    @Inject
    private Database database;

    public boolean exists(String name) {
        return database.tx().run(
            """
            RETURN EXISTS {
                MATCH (:MoeraNode {name: $name})
            } AS exists
            """,
            Map.of("name", name)
        ).single().get("exists").asBoolean();
    }

    public void createName(String name) {
        database.tx().run(
            """
            MERGE (n:MoeraNode {name: $name})
            """,
            Map.of("name", name)
        );
    }

    public void updateName(String name, WhoAmI whoAmI) {
        var args = new HashMap<String, Object>();
        args.put("name", name);
        args.put("fullName", whoAmI.getFullName());
        args.put("title", whoAmI.getTitle());

        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})
            SET n.fullName = $fullName, n.title = $title
            """,
            args
        );
    }

    public void rescanName(String name) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})
            SET n.scanProfile = null
            """,
            Map.of("name", name)
        );
    }

    public void addAvatar(String name, String mediaFileId, String shape) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name}), (mf:MediaFile {id: $mediaFileId})
            CREATE (n)-[:AVATAR {shape: $shape}]->(mf)
            """,
            Map.of(
                "name", name,
                "mediaFileId", mediaFileId,
                "shape", shape
            )
        );
    }

    public void removeAvatar(String name) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $name})-[a:AVATAR]->()
            DELETE a
            """,
            Map.of("name", name)
        );
    }

    public List<String> findNamesToScan(int limit) {
        return database.tx().run(
            """
            MATCH (n:MoeraNode)
            WHERE n.scanProfile IS NULL AND NOT (n)<-[:SCANS]-(:Job)
            LIMIT $limit
            RETURN n.name AS name
            """,
            Map.of("limit", limit)
        ).list(r -> r.get("name").asString());
    }

    public void assignScanJob(String name, UUID jobId) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name}), (j:Job {id: $jobId})
            MERGE (n)<-[:SCANS]-(j)
            """,
            Map.of(
                "name", name,
                "jobId", jobId.toString()
            )
        );
    }

    public void scanSucceeded(String name) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})
            SET n.scanProfile = true, n.profileScannedAt = $now
            """,
            Map.of(
                "name", name,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public void scanFailed(String name) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})
            SET n.scanProfile = false, n.profileScannedAt = $now
            """,
            Map.of(
                "name", name,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public void subscribed(String name, String subscriberId) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})
            SET n.subscriberId = $subscriberId
            """,
            Map.of(
                "name", name,
                "subscriberId", subscriberId
            )
        );
    }

    public void scanPeopleSucceeded(String name) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})
            SET n.scanPeople = true, n.peopleScannedAt = $now
            """,
            Map.of(
                "name", name,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public void scanPeopleFailed(String name) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})
            SET n.scanPeople = false, n.peopleScannedAt = $now
            """,
            Map.of(
                "name", name,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public void addFriendship(String name, String peerName) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name}), (p:MoeraNode {name: $peerName})
            MERGE (n)-[:FRIEND]->(p)
            """,
            Map.of(
                "name", name,
                "peerName", peerName
            )
        );
    }

    public void deleteFriendship(String name, String peerName) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $name})-[f:FRIEND]->(:MoeraNode {name: $peerName})
            DELETE f
            """,
            Map.of(
                "name", name,
                "peerName", peerName
            )
        );
    }

    public void addSubscription(String name, String peerName, String feedName) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name}), (p:MoeraNode {name: $peerName})
            MERGE (n)-[:SUBSCRIBED {feedName: $feedName}]->(p)
            """,
            Map.of(
                "name", name,
                "peerName", peerName,
                "feedName", feedName
            )
        );
    }

    public void deleteSubscription(String name, String peerName, String feedName) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $name})-[s:SUBSCRIBED {feedName: $feedName}]->(:MoeraNode {name: $peerName})
            DELETE s
            """,
            Map.of(
                "name", name,
                "peerName", peerName,
                "feedName", feedName
            )
        );
    }

    public void addBlocks(String name, String peerName, BlockedOperation blockedOperation) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name}), (p:MoeraNode {name: $peerName})
            MERGE (n)-[:BLOCKS {blockedOperation: $blockedOperation}]->(p)
            """,
            Map.of(
                "name", name,
                "peerName", peerName,
                "blockedOperation", blockedOperation.getValue()
            )
        );
    }

    public void deleteBlocks(String name, String peerName, BlockedOperation blockedOperation) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $name})
                  -[b:BLOCKS {blockedOperation: $blockedOperation}]->
                  (:MoeraNode {name: $peerName})
            DELETE b
            """,
            Map.of(
                "name", name,
                "peerName", peerName,
                "blockedOperation", blockedOperation.getValue()
            )
        );
    }

    public List<String> findNamesForCloseToUpdate(int limit) {
        return database.tx().run(
            """
            MATCH (n:MoeraNode)
            WHERE n.closeToUpdatedAt IS NULL OR n.closeToUpdatedAt < $deadline
            ORDER BY n.closeToUpdatedAt ASC
            LIMIT $limit
            RETURN n.name AS name
            """,
            Map.of(
                "deadline", Instant.now().minus(Workload.CLOSE_TO_UPDATE_PERIOD).toEpochMilli(),
                "limit", limit
            )
        ).stream().map(r -> r.get("name").asString()).toList();
    }

    public void closeToUpdated(String name) {
        database.tx().run(
            """
            MERGE (n:MoeraNode {name: $name})
            SET n.closeToUpdatedAt = $now
            """,
            Map.of(
                "name", name,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public record CloseNode(String name, boolean isFriend, boolean isSubscribed, List<Favor> favors) {
    }

    public void linkCloseNodes(String name) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})-[:FRIEND|SUBSCRIBED]->{1,2}(m:MoeraNode)
            WHERE n <> m
            WITH DISTINCT n, m
            MERGE (n)-[c:CLOSE_TO]->(m)
                ON CREATE
                    SET c.distance = 2.0, c.updatedAt = 0
            """,
            Map.of(
                "name", name
            )
        );
    }

    public List<CloseNode> findCloseNodes(String name, int limit, int favorLimit) {
        return database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})-[c:CLOSE_TO]->(m:MoeraNode)
            WHERE c.updatedAt <= $before
            LIMIT $limit
            RETURN
                m.name AS name,
                EXISTS((n)-[:FRIEND]->(m)) AS isFriend,
                EXISTS((n)-[:SUBSCRIBED]->(m)) AS isSubscribed,
                COLLECT {
                    MATCH (n)<-[:DONE_BY]-(f:Favor)-[:DONE_TO]->(m)
                    LIMIT $favorLimit
                    RETURN f
                } AS favors
            """,
            Map.of(
                "name", name,
                "before", Instant.now().minus(Workload.CLOSE_TO_UPDATE_PERIOD).toEpochMilli(),
                "limit", limit,
                "favorLimit", favorLimit
            )
        )
            .stream()
            .map(r -> new CloseNode(
                r.get("name").asString(),
                r.get("isFriend").asBoolean(),
                r.get("isSubscribed").asBoolean(),
                r.get("favors").asList(v -> new Favor(v.asNode()), Collections.emptyList())
            ))
            .toList();
    }

    public void setDistance(String name, String peerName, float distance) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $name})-[c:CLOSE_TO]->(:MoeraNode {name: $peerName})
            SET c.distance = $distance, c.updatedAt = $now
            """,
            Map.of(
                "name", name,
                "peerName", peerName,
                "distance", distance,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public void deleteCloseTo(String name, String peerName) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $name})-[c:CLOSE_TO]->(:MoeraNode {name: $peerName})
            DELETE c
            """,
            Map.of(
                "name", name,
                "peerName", peerName
            )
        );
    }

    public List<String> findNamesForCloseToCleanup(int limit) {
        return database.tx().run(
            """
            MATCH (n:MoeraNode)
            WHERE n.closeToCleanedUpAt IS NULL OR n.closeToCleanedUpAt < $deadline
            ORDER BY n.closeToCleanedUpAt ASC
            LIMIT $limit
            RETURN n.name AS name
            """,
            Map.of(
                "deadline", Instant.now().minus(Workload.CLOSE_TO_CLEANUP_PERIOD).toEpochMilli(),
                "limit", limit
            )
        ).stream().map(r -> r.get("name").asString()).toList();
    }

    public void closeToCleanedUp(String name) {
        database.tx().run(
            """
            MERGE (n:MoeraNode {name: $name})
            SET n.closeToCleanedUpAt = $now
            """,
            Map.of(
                "name", name,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public void cleanupCloseTo(String name) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})-[c:CLOSE_TO]->(m:MoeraNode)
            WHERE NOT EXISTS {
                MATCH (n)-[:FRIEND|SUBSCRIBED]->{1,2}(m)
            }
            DELETE c
            """,
            Map.of("name", name)
        );
    }

    public boolean isScannedTimeline(String name) {
        return database.tx().run(
            """
            OPTIONAL MATCH (n:MoeraNode {name: $name})
            RETURN n.scanTimeline IS NOT NULL AS scan
            """,
            Map.of(
                "name", name
            )
        ).single().get("scan").asBoolean();
    }

    public void scanTimelineSucceeded(String name) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})
            SET n.scanTimeline = true, n.timelineScannedAt = $now
            """,
            Map.of(
                "name", name,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public void scanTimelineFailed(String name) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})
            SET n.scanTimeline = false, n.timelineScannedAt = $now
            """,
            Map.of(
                "name", name,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public boolean isScannedSheriff(String name) {
        return database.tx().run(
            """
            OPTIONAL MATCH (n:MoeraNode {name: $name})
            RETURN n.scanSheriff IS NOT NULL AS scan
            """,
            Map.of(
                "name", name
            )
        ).single().get("scan").asBoolean();
    }

    public boolean isScanSheriffSucceeded(String name) {
        return database.tx().run(
            """
            OPTIONAL MATCH (n:MoeraNode {name: $name})
            RETURN n.scanSheriff IS NOT NULL AND n.scanSheriff = true AS scan
            """,
            Map.of(
                "name", name
            )
        ).single().get("scan").asBoolean();
    }

    public void scanSheriffSucceeded(String name) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})
            SET n.scanSheriff = true, n.sheriffScannedAt = $now
            """,
            Map.of(
                "name", name,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public void scanSheriffFailed(String name) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})
            SET n.scanSheriff = false, n.sheriffScannedAt = $now
            """,
            Map.of(
                "name", name,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public void sheriffMark(String sheriffName, String name) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})
            WHERE n.sheriffMarks IS NULL OR NOT ($sheriffName IN n.sheriffMarks)
            SET n.sheriffMarks = CASE
                WHEN n.sheriffMarks IS NULL THEN [$sheriffName]
                ELSE n.sheriffMarks + [$sheriffName]
            END
            """,
            Map.of(
                "sheriffName", sheriffName,
                "name", name
            )
        );
    }

    public void sheriffUnmark(String sheriffName, String name) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})
            WHERE n.sheriffMarks IS NOT NULL AND $sheriffName IN n.sheriffMarks
            SET n.sheriffMarks = [mark IN n.sheriffMarks WHERE mark <> $sheriffName]
            """,
            Map.of(
                "sheriffName", sheriffName,
                "name", name
            )
        );
    }

    public void sheriffMarkOwner(String sheriffName, String name) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})
            WHERE n.ownerSheriffMarks IS NULL OR NOT ($sheriffName IN n.ownerSheriffMarks)
            SET n.ownerSheriffMarks = CASE
                WHEN n.ownerSheriffMarks IS NULL THEN [$sheriffName]
                ELSE n.ownerSheriffMarks + [$sheriffName]
            END
            """,
            Map.of(
                "sheriffName", sheriffName,
                "name", name
            )
        );
    }

    public void sheriffUnmarkOwner(String sheriffName, String name) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})
            WHERE n.ownerSheriffMarks IS NOT NULL AND $sheriffName IN n.ownerSheriffMarks
            SET n.ownerSheriffMarks = [mark IN n.ownerSheriffMarks WHERE mark <> $sheriffName]
            """,
            Map.of(
                "sheriffName", sheriffName,
                "name", name
            )
        );
    }

    public void addDontRecommend(String name, String peerName) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name}), (p:MoeraNode {name: $peerName})
            MERGE (n)-[:DONT_RECOMMEND]->(p)
            """,
            Map.of(
                "name", name,
                "peerName", peerName
            )
        );
    }

    public void deleteDontRecommend(String name, String peerName) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})-[d:DONT_RECOMMEND]->(p:MoeraNode {name: $peerName})
            DELETE d
            """,
            Map.of(
                "name", name,
                "peerName", peerName
            )
        );
    }

    public void updateActivity(int limit) {
        database.tx().run(
            """
            MATCH (n:MoeraNode)
            WHERE n.activityUpdatedAt IS NULL OR n.activityUpdatedAt < $deadline
            ORDER BY n.activityUpdatedAt ASC
            LIMIT $limit
            WITH n
            MATCH (n)<-[:SOURCE]-(p:Posting)
            WHERE p.createdAt > $bottom
            WITH n, (1.0 - (toFloat($now - p.createdAt * 1000) / 3600000 / $decayHours)^2) AS rest
            WITH n, sum(rest) AS activity
            SET n.activity = activity, n.activityUpdatedAt = $now
            """,
            Map.of(
                "deadline", Instant.now().minus(Workload.ACTIVITY_UPDATE_PERIOD).toEpochMilli(),
                "limit", limit,
                "bottom", Instant.now().minus(Duration.ofHours(POSTING_ACTIVITY_DECAY_HOURS)).getEpochSecond(),
                "decayHours", POSTING_ACTIVITY_DECAY_HOURS,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

}
