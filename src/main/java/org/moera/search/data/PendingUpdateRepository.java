package org.moera.search.data;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.neo4j.driver.types.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.stereotype.Component;

@Component
public class PendingUpdateRepository {

    private static final Logger log = LoggerFactory.getLogger(PendingUpdateRepository.class);

    @Inject
    private Database database;

    @Inject
    private ObjectMapper objectMapper;

    @Inject
    private AutowireCapableBeanFactory autowireCapableBeanFactory;

    public void create(PendingUpdate<?> update) throws JsonProcessingException {
        var args = new HashMap<String, Object>();
        args.put("id", update.getId().toString());
        args.put("type", update.getClass().getCanonicalName());
        args.put(
            "jobParameters",
            update.getJobParameters() != null ? objectMapper.writeValueAsString(update.getJobParameters()) : null
        );
        args.put("createdAt", update.getCreatedAt().toEpochMilli());

        database.tx().run(
            """
            CREATE (:PendingUpdate {
                id: $id,
                type: $type,
                jobParameters: $jobParameters,
                createdAt: $createdAt
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

    public List<PendingUpdate<?>> findAll() {
        return database.tx().run(
            """
            MATCH (pu:PendingUpdate)
            ORDER BY pu.createdAt ASC
            RETURN pu
            """
        )
            .stream()
            .map(r -> r.get("pu").asNode())
            .map(this::loadPendingUpdate)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private PendingUpdate<?> loadPendingUpdate(Node node) {
        var update = createPendingUpdate(node.get("type").asString());
        if (update != null) {
            update.setId(UUID.fromString(node.get("id").asString()));
            update.decodeJobParameters(node.get("jobParameters").asString(null));
            update.setCreatedAt(Instant.ofEpochMilli(node.get("createdAt").asLong()));
        }
        return update;
    }

    private PendingUpdate<?> createPendingUpdate(String type) {
        PendingUpdate<?> update = null;
        try {
            update = (PendingUpdate<?>) Class.forName(type).getConstructor().newInstance();
            autowireCapableBeanFactory.autowireBean(update);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            log.error("Cannot create a PendingUpdate", e);
        } catch (NoSuchMethodException e) {
            log.error("Cannot find a PendingUpdate constructor", e);
        } catch (ClassNotFoundException e) {
            log.error("Cannot find a PendingUpdate class", e);
        }
        return update;
    }

}
