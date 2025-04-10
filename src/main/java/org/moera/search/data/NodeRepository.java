package org.moera.search.data;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.inject.Inject;

import org.moera.lib.node.types.BlockedOperation;
import org.moera.lib.node.types.SearchNodeInfo;
import org.moera.lib.node.types.WhoAmI;
import org.moera.search.model.SearchNodeInfoUtil;
import org.springframework.stereotype.Component;

@Component
public class NodeRepository {

    @Inject
    private Database database;

    public boolean existsName(String name) {
        return database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})
            RETURN count(n) > 0 AS exists
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
        args.put("now", Instant.now().toEpochMilli());

        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})
            SET n.fullName = $fullName, n.title = $title, n.scanProfile = true, n.profileScannedAt = $now
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

    public List<String> findNamesToSubscribe(int limit) {
        return database.tx().run(
            """
            MATCH (n:MoeraNode)
            WHERE n.subscribe IS NULL AND NOT (n)<-[:SUBSCRIBES]-(:Job)
            LIMIT $limit
            RETURN n.name AS name
            """,
            Map.of("limit", limit)
        ).list(r -> r.get("name").asString());
    }

    public void assignSubscribeJob(String name, UUID jobId) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name}), (j:Job {id: $jobId})
            MERGE (n)<-[:SUBSCRIBES]-(j)
            """,
            Map.of(
                "name", name,
                "jobId", jobId.toString()
            )
        );
    }

    public void subscribed(String name, String subscriberId) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})
            SET n.subscribe = true, n.subscribedAt = $now, n.subscriberId = $subscriberId
            """,
            Map.of(
                "name", name,
                "subscriberId", subscriberId,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public void subscribeFailed(String name) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})
            SET n.subscribe = false, n.subscribedAt = $now
            """,
            Map.of(
                "name", name,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public List<String> findNamesToScanPeople(int limit) {
        return database.tx().run(
            """
            MATCH (n:MoeraNode)
            WHERE n.scanPeople IS NULL AND NOT (n)<-[:SCANS_PEOPLE]-(:Job)
            LIMIT $limit
            RETURN n.name AS name
            """,
            Map.of("limit", limit)
        ).list(r -> r.get("name").asString());
    }

    public void assignScanPeopleJob(String name, UUID jobId) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name}), (j:Job {id: $jobId})
            MERGE (n)<-[:SCANS_PEOPLE]-(j)
            """,
            Map.of(
                "name", name,
                "jobId", jobId.toString()
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
            MERGE (n)-[b:BLOCKS {blockedOperation: $blockedOperation}]->(p)
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

    public List<SearchNodeInfo> searchByNamePrefix(String clientName, String prefix, int limit, boolean blocked) {
        var args = new HashMap<String, Object>();
        args.put("clientName", clientName);
        args.put("prefix", prefix.toLowerCase());
        args.put("limit", limit);
        args.put("blocked", blocked);

        return database.tx().run(
            """
            MATCH (n:MoeraNode)
            WHERE lower(n.name) STARTS WITH $prefix
            WITH
                n,
                CASE
                    WHEN $clientName IS NULL THEN false
                    ELSE EXISTS((n)<-[:BLOCKS]-(:MoeraNode {name: $clientName}))
                END AS blocked
            WHERE blocked = $blocked
            LIMIT $limit
            OPTIONAL MATCH (n)-[a:AVATAR]->(mf:MediaFile)
            RETURN n, a.shape AS shape, mf
            """,
            args
        ).stream().map(r -> {
            var node = r.get("n").asNode();
            var avatarShape = r.get("shape").asString(null);
            var avatar = r.get("mf").isNull() ? null : new MediaFile(r.get("mf").asNode());
            return SearchNodeInfoUtil.build(node, avatar, avatarShape, blocked);
        }).toList();
    }

    public List<SearchNodeInfo> searchByFullNamePrefix(String clientName, String prefix, int limit, boolean blocked) {
        var terms = prefix.split("\\s+");
        String query = String.join("* ", terms) + "*";

        var args = new HashMap<String, Object>();
        args.put("clientName", clientName);
        args.put("query", query);
        args.put("limit", limit);
        args.put("blocked", blocked);

        return database.tx().run(
            """
            CALL db.index.fulltext.queryNodes("moera_node_full_name", $query) YIELD node AS n, score
            WITH
                n,
                CASE
                    WHEN $clientName IS NULL THEN false
                    ELSE EXISTS((n)<-[:BLOCKS]-(:MoeraNode {name: $clientName}))
                END AS blocked
            WHERE blocked = $blocked
            LIMIT $limit
            OPTIONAL MATCH (n)-[a:AVATAR]->(mf:MediaFile)
            RETURN n, a.shape AS shape, mf
            """,
            args
        ).stream().map(r -> {
            var node = r.get("n").asNode();
            var avatarShape = r.get("shape").asString(null);
            var avatar = r.get("mf").isNull() ? null : new MediaFile(r.get("mf").asNode());
            return SearchNodeInfoUtil.build(node, avatar, avatarShape, blocked);
        }).toList();
    }

}
