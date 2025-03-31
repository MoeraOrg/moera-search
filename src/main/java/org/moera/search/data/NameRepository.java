package org.moera.search.data;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.types.WhoAmI;
import org.springframework.stereotype.Component;

@Component
public class NameRepository {

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
                ON CREATE SET n.scanProfile = true
            """,
            Map.of("name", name)
        );
    }

    public void updateName(String name, WhoAmI whoAmI) {
        String avatar;
        try {
            avatar = objectMapper.writeValueAsString(whoAmI.getAvatar());
        } catch (JsonProcessingException e) {
            throw new DatabaseException("Error encoding MoeraNode.avatar", e);
        }

        var args = new HashMap<String, Object>();
        args.put("name", name);
        args.put("fullName", whoAmI.getFullName());
        args.put("title", whoAmI.getTitle());
        args.put("avatar", avatar);
        args.put("now", Instant.now().toEpochMilli());

        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $name})
            SET n.fullName = $fullName, n.title = $title, n.avatar = $avatar, n.scanProfile = null,
                n.profileScannedAt = $now
            """,
            args
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
            MATCH (n:MoeraNode {scanProfile: true})
            WHERE NOT (n)<-[:SCANS]-(:Job)
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
            SET n.scanProfile = null, n.profileScannedAt = $now, n.profileScanFailed = true
            """,
            Map.of(
                "name", name,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

}
