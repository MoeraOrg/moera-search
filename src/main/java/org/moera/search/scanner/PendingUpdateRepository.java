package org.moera.search.scanner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.types.SearchContentUpdateType;
import org.moera.search.data.Database;
import org.springframework.stereotype.Component;

@Component
public class PendingUpdateRepository {

    @Inject
    private Database database;

    @Inject
    private ObjectMapper objectMapper;

    public void create(PendingUpdate update) throws JsonProcessingException {
        var args = new HashMap<String, Object>();
        args.put("id", update.id().toString());
        args.put("nodeName", update.nodeName());
        args.put("type", update.type().getValue());
        args.put("details", update.details() != null ? objectMapper.writeValueAsString(update.details()) : null);
        args.put("createdAt", update.createdAt().toEpochMilli());
        args.put("jobKey", update.jobKey());

        database.tx().run(
            """
            CREATE (:PendingUpdate {
                id: $id,
                nodeName: $nodeName,
                type: $type,
                details: $details,
                createdAt: $createdAt,
                jobKey: $jobKey
            })
            """,
            args
        );
    }

    public void deleteById(UUID id) {
        database.tx().run(
            """
            MATCH (pu:PendingUpdate {id: $id})
            DETACH DELETE pu
            """,
            Map.of(
                "id", id.toString()
            )
        );
    }

    public List<PendingUpdate> findAll(BiFunction<SearchContentUpdateType, String, Object> decodeDetails) {
        return database.tx().run(
            """
            MATCH (pu:PendingUpdate)
            ORDER BY pu.createdAt ASC
            RETURN pu
            """
        )
            .stream()
            .map(r -> r.get("pu").asNode())
            .map(pu -> {
                SearchContentUpdateType type = SearchContentUpdateType.forValue(pu.get("type").asString());
                return new PendingUpdate(
                    UUID.fromString(pu.get("id").asString()),
                    pu.get("nodeName").asString(),
                    type,
                    decodeDetails.apply(type, pu.get("details").asString(null)),
                    Instant.ofEpochMilli(pu.get("createdAt").asLong()),
                    pu.get("jobKey").asString()
                );
            })
            .collect(Collectors.toCollection(ArrayList::new));
    }

}
