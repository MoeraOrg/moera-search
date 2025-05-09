package org.moera.search.data;

import java.util.Map;
import jakarta.inject.Inject;

import org.moera.search.util.MomentFinder;
import org.springframework.stereotype.Component;

@Component
public class EntryRepository {

    @Inject
    private Database database;

    private final MomentFinder momentFinder = new MomentFinder();

    public boolean momentExists(long moment) {
        return database.tx().run(
            """
            RETURN EXISTS {
                MATCH (e:Entry {moment: $moment})
            } AS e
            """,
            Map.of(
                "moment", moment
            )
        ).single().get("e").asBoolean();
    }

    public void allocateMoment(String documentId, long createdAt) {
        var moment = momentFinder.find(m -> !momentExists(m), createdAt * 1000);
        database.tx().run(
            """
            MATCH (e:Entry {documentId: $documentId})
            SET e.moment = $moment
            """,
            Map.of(
                "documentId", documentId,
                "moment", moment
            )
        );
    }

}
