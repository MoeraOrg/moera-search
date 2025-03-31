package org.moera.search.data;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import jakarta.inject.Inject;

import org.springframework.stereotype.Component;

@Component
public class MediaFileRepository {

    @Inject
    private Database database;

    public MediaFile findById(String id) {
        var value = database.tx().run(
            """
            OPTIONAL MATCH (mf:MediaFile {id: $id})
            RETURN mf
            """,
            Map.of("id", id)
        ).single().get("mf");
        return !value.isNull() ? new MediaFile(value.asNode()) : null;
    }

    public List<MediaFile> findAllExposed(int offset, int limit) {
        return database.tx().run(
            """
            MATCH (mf:MediaFile {exposed: true})
            ORDER BY mf.id
            OFFSET $offset
            LIMIT $limit
            RETURN mf
            """,
            Map.of(
                "offset", offset,
                "limit", limit
            )
        ).stream().map(r -> new MediaFile(r.get("mf").asNode())).toList();
    }

    public void create(MediaFile mediaFile) {
        database.tx().run(
            """
            MERGE (mf:MediaFile {id: $id})
            SET
                mf.mimeType=$mimeType,
                mf.sizeX=$sizeX,
                mf.sizeY=$sizeY,
                mf.orientation=$orientation,
                mf.fileSize=$fileSize,
                mf.exposed=$exposed,
                mf.digest=$digest,
                mf.createdAt=$createdAt
            """,
            mediaFile.asMap()
        );
    }

    public List<MediaFile> deleteUnused() {
        return database.tx().run(
            """
            MATCH (mf:MediaFile)
            WHERE NOT ()-[:AVATAR]->(mf) AND mf.createdAt < $deadline
            DELETE mf
            RETURN mf
            """,
            Map.of("deadline", Instant.now().minus(3, ChronoUnit.HOURS).toEpochMilli())
        ).stream().map(r -> new MediaFile(r.get("mf").asNode())).toList();
    }

}
