package org.moera.search.data;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.inject.Inject;

import org.springframework.stereotype.Component;

@Component
public class JobRepository {

    @Inject
    private Database database;

    public List<PendingJob> findAllBefore(long before) {
        return database.tx().run(
            """
            MATCH (j:Job)
            WHERE j.waitUntil IS NULL OR j.waitUntil < $before
            RETURN j
            """,
            Map.of("before", before)
        ).stream().map(r -> new PendingJob(r.get("j").asNode())).toList();
    }

    public UUID create(String jobType, String jobKey, String parameters, String state) {
        UUID id = UUID.randomUUID();
        var args = new HashMap<String, Object>();
        args.put("id", id.toString());
        args.put("jobType", jobType);
        args.put("jobKey", jobKey);
        args.put("parameters", parameters);
        args.put("state", state);

        database.tx().run(
            """
            CREATE (:Job {id: $id, jobType: $jobType, jobKey: $jobKey, parameters: $parameters, state: $state})
            """,
            args
        );

        return id;
    }

    public void updateState(UUID id, String state, int retries, Long waitUntil) {
        var args = new HashMap<String, Object>();
        args.put("id", id.toString());
        args.put("state", state);
        args.put("retries", retries);
        args.put("waitUntil", waitUntil);

        database.tx().run(
            """
            MATCH (j:Job {id: $id})
            SET j.state = $state, j.retries = $retries, j.waitUntil = $waitUntil
            """,
            args
        );
    }

    public void delete(UUID id) {
        database.tx().run(
            """
            MATCH (j:Job {id: $id})
            DETACH DELETE j
            """,
            Map.of("id", id.toString())
        );
    }

    public int countRunningByType(String jobType) {
        return database.tx().run(
            """
            MATCH (j:Job {jobType: $jobType})
            WHERE j.waitUntil IS NULL OR j.waitUntil >= $now
            RETURN count(j) AS count
            """,
            Map.of(
                "jobType", jobType,
                "now", Instant.now().minus(1, ChronoUnit.HOURS).toEpochMilli()
            )
        ).single().get("count").asInt();
    }

    public boolean keyExists(String jobKey) {
        return database.tx().run(
            """
            RETURN EXISTS {
                MATCH (j:Job {jobKey: $jobKey})
            } AS e
            """,
            Map.of(
                "jobKey", jobKey
            )
        ).single().get("e").asBoolean();
    }

}
