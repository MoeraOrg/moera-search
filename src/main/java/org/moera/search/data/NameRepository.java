package org.moera.search.data;

import java.util.Map;
import jakarta.inject.Inject;

import org.springframework.stereotype.Component;

@Component
public class NameRepository {

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
            CREATE (n:MoeraNode {name: $name, scanProfile: true})
            """,
            Map.of("name", name)
        );
    }

}
