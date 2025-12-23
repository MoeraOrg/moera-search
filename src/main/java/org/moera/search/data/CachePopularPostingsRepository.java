package org.moera.search.data;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.types.RecommendedPostingInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Component
public class CachePopularPostingsRepository {

    private static final Logger log = LoggerFactory.getLogger(CachePopularPostingsRepository.class);

    @Inject
    private Database database;

    @Inject
    private ObjectMapper objectMapper;

    private List<RecommendedPostingInfo> getCached(String fieldName, String sheriffName) {
        var query =
            """
            OPTIONAL MATCH (ch:CachePopularPostings {sheriffName: $sheriffName})
            ORDER BY ch.deadline DESC
            LIMIT 1
            RETURN ch.%s AS value
            """.formatted(fieldName);

        var args = new HashMap<String, Object>();
        args.put("sheriffName", sheriffName != null ? sheriffName : "");

        return deserialize(database.tx().run(query, args).single().get("value").asString(null));
    }

    private void setCached(String fieldName, String sheriffName, List<RecommendedPostingInfo> postings) {
        var query =
            """
            MERGE (ch:CachePopularPostings {sheriffName: $sheriffName})
                ON CREATE
                    SET ch.deadline = $deadline
            SET ch.%s = $value
            """.formatted(fieldName);

        var args = new HashMap<String, Object>();
        args.put("sheriffName", sheriffName != null ? sheriffName : "");
        args.put("value", serialize(postings));
        args.put("deadline", Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli());

        database.tx().run(query, args);
    }

    public String serialize(List<RecommendedPostingInfo> postings) {
        if (postings == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(postings);
        } catch (Exception e) {
            log.error("Error serializing recommendations", e);
            return null;
        }
    }

    public List<RecommendedPostingInfo> deserialize(String json) {
        if (ObjectUtils.isEmpty(json)) {
            return null;
        }

        try {
            return objectMapper.readValue(json, new TypeReference<>(){});
        } catch (Exception e) {
            log.error("Error deserializing recommendations", e);
            return null;
        }
    }

    public List<RecommendedPostingInfo> getPopular(String sheriffName) {
        return getCached("popular", sheriffName);
    }

    public void setPopular(String sheriffName, List<RecommendedPostingInfo> postings) {
        setCached("popular", sheriffName, postings);
    }

    public List<RecommendedPostingInfo> getPopularReading(String sheriffName) {
        return getCached("popularReading", sheriffName);
    }

    public void setPopularReading(String sheriffName, List<RecommendedPostingInfo> postings) {
        setCached("popularReading", sheriffName, postings);
    }

    public List<RecommendedPostingInfo> getPopularCommenting(String sheriffName) {
        return getCached("popularCommenting", sheriffName);
    }

    public void setPopularCommenting(String sheriffName, List<RecommendedPostingInfo> postings) {
        setCached("popularCommenting", sheriffName, postings);
    }

    public void deleteExpired() {
        database.tx().run(
            """
            MATCH (ch:CachePopularPostings)
            WHERE ch.deadline < $now
            DELETE ch
            """,
            Map.of("now", Instant.now().toEpochMilli())
        );
    }

}
