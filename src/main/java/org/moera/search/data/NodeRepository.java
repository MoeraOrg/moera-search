package org.moera.search.data;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import jakarta.inject.Inject;

import org.moera.lib.node.types.BlockedOperation;
import org.moera.lib.node.types.SearchNodeInfo;
import org.moera.lib.node.types.WhoAmI;
import org.moera.search.Workload;
import org.moera.search.api.model.SearchNodeInfoUtil;
import org.moera.search.util.Util;
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

    public List<SearchNodeInfo> searchCloseByNamePrefix(String clientName, String prefix, int limit) {
        var args = new HashMap<String, Object>();
        args.put("clientName", clientName);
        args.put("prefix", prefix.toLowerCase());
        args.put("limit", limit);

        return database.tx().run(
            """
            MATCH (:MoeraNode {name: $clientName})-[c:CLOSE_TO]->(n:MoeraNode)
            WHERE lower(n.name) STARTS WITH $prefix
            WITH n, c.distance AS distance
            ORDER BY distance DESC
            LIMIT $limit
            OPTIONAL MATCH (n)-[a:AVATAR]->(mf:MediaFile)
            RETURN n, distance, a.shape AS shape, mf
            """,
            args
        ).stream().map(r -> {
            var node = r.get("n").asNode();
            var distance = r.get("distance").asFloat();
            var avatarShape = r.get("shape").asString(null);
            var avatar = r.get("mf").isNull() ? null : new MediaFile(r.get("mf").asNode());
            return SearchNodeInfoUtil.build(node, avatar, avatarShape, distance);
        }).toList();
    }

    public List<SearchNodeInfo> searchCloseByFullNamePrefix(String clientName, String prefix, int limit) {
        var args = new HashMap<String, Object>();
        args.put("clientName", clientName);
        args.put("prefix", prefixRegex(prefix));
        args.put("limit", limit);

        return database.tx().run(
            """
            MATCH (:MoeraNode {name: $clientName})-[c:CLOSE_TO]->(n:MoeraNode)
            WHERE n.fullName =~ $prefix
            WITH n, c.distance AS distance
            ORDER BY c.distance DESC
            LIMIT $limit
            OPTIONAL MATCH (n)-[a:AVATAR]->(mf:MediaFile)
            RETURN n, distance, a.shape AS shape, mf
            """,
            args
        ).stream().map(r -> {
            var node = r.get("n").asNode();
            var distance = r.get("distance").asFloat();
            var avatarShape = r.get("shape").asString(null);
            var avatar = r.get("mf").isNull() ? null : new MediaFile(r.get("mf").asNode());
            return SearchNodeInfoUtil.build(node, avatar, avatarShape, distance);
        }).toList();
    }

    private String prefixRegex(String prefix) {
        StringBuilder buf = new StringBuilder("(?i)");

        var terms = prefix.split("\\s+", 3);
        for (int n0 = 0; n0 < terms.length; n0++) {
            if (terms.length == 1) {
                buf.append(termsRegex(terms, n0, -1, -1));
                continue;
            }

            for (int n1 = 0; n1 < terms.length; n1++) {
                if (n1 == n0) {
                    continue;
                }
                if (terms.length == 2) {
                    buf.append(termsRegex(terms, n0, n1, -1));
                    continue;
                }

                for (int n2 = 0; n2 < terms.length; n2++) {
                    if (n2 == n1 || n2 == n0) {
                        continue;
                    }
                    buf.append(termsRegex(terms, n0, n1, n2));
                }
            }
        }

        return buf.substring(0, buf.length() - 1);
    }

    private String termsRegex(String[] terms, int n0, int n1, int n2) {
        StringBuilder buf = new StringBuilder("(?:.*");
        if (n0 >= 0) {
            buf.append(termRegex(terms[n0]));
        }
        if (n1 >= 0) {
            buf.append(termRegex(terms[n1]));
        }
        if (n2 >= 0) {
            buf.append(termRegex(terms[n2]));
        }
        buf.append(")|");
        return buf.toString();
    }

    private String termRegex(String term) {
        return "\\b" + Pattern.quote(term) + ".*";
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
        var terms = Util.escapeLucene(prefix).split("\\s+");
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

    public record CloseNode(String name, boolean isFriend, boolean isSubscribed) {
    }

    public List<CloseNode> findCloseNodes(String name) {
        return database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})-[:FRIEND|SUBSCRIBED]->{1,2}(m:MoeraNode)
            WHERE n <> m
            WITH DISTINCT n, m
            RETURN
                m.name AS name,
                EXISTS((n)-[:FRIEND]->(m)) AS isFriend,
                EXISTS((n)-[:SUBSCRIBED]->(m)) AS isSubscribed
            """,
            Map.of(
                "name", name
            )
        )
            .stream()
            .map(r -> new CloseNode(
                r.get("name").asString(),
                r.get("isFriend").asBoolean(),
                r.get("isSubscribed").asBoolean()
            ))
            .toList();
    }

    public void setDistance(String name, String peerName, float distance) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name}), (m:MoeraNode {name: $peerName})
            MERGE (n)-[c:CLOSE_TO]->(m)
            SET c.distance = $distance
            """,
            Map.of(
                "name", name,
                "peerName", peerName,
                "distance", distance
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

    public void cleanupCloseTo() {
        database.tx().run(
            """
            MATCH (n:MoeraNode)-[c:CLOSE_TO]->(m:MoeraNode)
            WHERE NOT EXISTS {
                MATCH (n)-[:FRIEND|SUBSCRIBED]->{1,2}(m)
            }
            DELETE c
            """
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

}
