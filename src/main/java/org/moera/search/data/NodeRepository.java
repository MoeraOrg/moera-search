package org.moera.search.data;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.types.SearchNodeInfo;
import org.moera.lib.node.types.WhoAmI;
import org.moera.search.model.SearchNodeInfoUtil;
import org.springframework.stereotype.Component;

@Component
public class NodeRepository {

    @Inject
    private Database database;

    @Inject
    private ObjectMapper objectMapper;

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

    public List<SearchNodeInfo> searchByNamePrefix(String prefix, int limit) {
        return database.tx().run(
            """
            MATCH (n:MoeraNode)
            WHERE lower(n.name) STARTS WITH $prefix
            LIMIT $limit
            OPTIONAL MATCH (n)-[a:AVATAR]->(mf:MediaFile)
            RETURN n, a.shape AS shape, mf
            """,
            Map.of(
                "prefix", prefix.toLowerCase(),
                "limit", limit
            )
        ).stream().map(r -> {
            var node = r.get("n").asNode();
            var avatarShape = r.get("shape").asString(null);
            var avatar = r.get("mf").isNull() ? null : new MediaFile(r.get("mf").asNode());
            return SearchNodeInfoUtil.build(node, avatar, avatarShape);
        }).toList();
    }

    public List<SearchNodeInfo> searchByFullNamePrefix(String prefix, int limit) {
        var terms = prefix.split("\\s+");
        String query = String.join("* ", terms) + "*";
        return database.tx().run(
            """
            CALL db.index.fulltext.queryNodes("moera_node_full_name", $query) YIELD node AS n, score
            LIMIT $limit
            OPTIONAL MATCH (n)-[a:AVATAR]->(mf:MediaFile)
            RETURN n, a.shape AS shape, mf
            """,
            Map.of(
                "query", query,
                "limit", limit
            )
        ).stream().map(r -> {
            var node = r.get("n").asNode();
            var avatarShape = r.get("shape").asString(null);
            var avatar = r.get("mf").isNull() ? null : new MediaFile(r.get("mf").asNode());
            return SearchNodeInfoUtil.build(node, avatar, avatarShape);
        }).toList();
    }

}
