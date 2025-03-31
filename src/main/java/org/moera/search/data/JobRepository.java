package org.moera.search.data;

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
        ).list(PendingJob::new);
    }

    public UUID create(String jobType, String parameters, String state) {
        UUID id = UUID.randomUUID();
        database.tx().run(
            """
            CREATE (j:Job {id: $id, jobType: $jobType, parameters: $parameters, state: $state})
            """,
            Map.of(
                "id", id.toString(),
                "jobType", jobType,
                "parameters", parameters,
                "state", state
            )
        );
        return id;
    }

    public void updateState(UUID id, String state, int retries, Long waitUntil) {
        database.tx().run(
            """
            MATCH (j:Job {id: $id})
            SET j.state = $state, j.retries = $retries, j.waitUntil = $waitUntil
            """,
            Map.of(
                "id", id.toString(),
                "state", state,
                "retries", retries,
                "waitUntil", waitUntil
            )
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

}
