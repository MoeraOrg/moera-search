package org.moera.search.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.types.SearchEntryInfo;
import org.moera.lib.node.types.SearchEntryOperations;
import org.moera.lib.node.types.SearchEntryType;
import org.moera.lib.node.types.SearchRepliedTo;
import org.moera.lib.node.types.body.Body;
import org.moera.lib.node.types.principal.Principal;
import org.moera.search.api.Feed;
import org.moera.search.api.model.AvatarImageUtil;
import org.moera.search.api.model.PublicMediaFileInfoUtil;
import org.moera.search.util.MomentFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EntryRepository {

    private static final Logger log = LoggerFactory.getLogger(EntryRepository.class);

    @Inject
    private Database database;

    @Inject
    private ObjectMapper objectMapper;

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

    public List<SearchEntryInfo> findEntriesByHashtag(
        SearchEntryType entryType,
        List<String> hashtags,
        String publisherName,
        boolean inNewsfeed,
        Integer minImageCount,
        Integer maxImageCount,
        Boolean videoPresent,
        String sheriffName,
        boolean signedIn,
        Long before,
        Long after,
        int limit
    ) {
        var query = new StringBuilder();
        var args = new HashMap<String, Object>();

        String hashtagMatch;
        if (hashtags.size() == 1) {
            hashtagMatch = "{name: $hashtag}";
            args.put("hashtag", hashtags.get(0));
        } else {
            hashtagMatch = "WHERE h.name IN $hashtags";
            args.put("hashtags", hashtags);
        }
        query.append("MATCH (h:Hashtag %s)<-[:MARKED_WITH]-".formatted(hashtagMatch));
        query.append(
            switch (entryType) {
                case ALL -> "(e:Entry)(()-[:UNDER]->(p:Posting)){0,1}()";
                case POSTING -> "(e:Posting)";
                case COMMENT -> "(e:Comment)-[:UNDER]->(p:Posting)";
            }
        );
        query.append("-[:SOURCE]->(n:MoeraNode), (e)-[:OWNER]->(o:MoeraNode)\n");
        if (after != null) {
            query.append("WHERE e.moment IS NOT NULL AND e.moment > $after");
            args.put("after", after);
        } else if (before != null) {
            query.append("WHERE e.moment IS NOT NULL AND e.moment <= $before");
            args.put("before", before);
        }
        if (publisherName != null) {
            var publication =
                """
                <-[:CONTAINS]-(:Publication {feedName: $feedName})-[:PUBLISHED_IN]->(:MoeraNode {name: $publisherName})
                """;
            query.append(
                switch (entryType) {
                    case ALL ->
                        " AND (EXISTS((e)" + publication + ") OR EXISTS{WITH p[0] AS pr MATCH (pr)" + publication + "})";
                    case POSTING -> " AND EXISTS((e)" + publication + ")";
                    case COMMENT -> " AND EXISTS((p)" + publication + ")";
                }
            );
            args.put("feedName", inNewsfeed ? Feed.NEWS : Feed.TIMELINE);
            args.put("publisherName", publisherName);
        }
        if (minImageCount != null) {
            query.append(" AND e.imageCount >= $minImageCount");
            args.put("minImageCount", minImageCount);
        }
        if (maxImageCount != null) {
            query.append(" AND e.imageCount <= $maxImageCount");
            args.put("maxImageCount", maxImageCount);
        }
        if (videoPresent != null) {
            query.append(" AND e.videoPresent = $videoPresent");
            args.put("videoPresent", videoPresent);
        }
        if (sheriffName != null) {
            query.append(" AND (n.sheriffMarks IS NULL OR NOT ($sheriffName IN n.sheriffMarks))");
            query.append(" AND (o.ownerSheriffMarks IS NULL OR NOT ($sheriffName IN o.ownerSheriffMarks))");
            query.append(" AND (e.sheriffMarks IS NULL OR NOT ($sheriffName IN e.sheriffMarks))");
            if (entryType == SearchEntryType.ALL) {
                query.append(
                    " AND (size(p) = 0 OR p[0].sheriffMarks IS NULL OR NOT ($sheriffName IN p[0].sheriffMarks))"
                );
            } else if (entryType == SearchEntryType.COMMENT) {
                query.append(" AND (p.sheriffMarks IS NULL OR NOT ($sheriffName IN p.sheriffMarks))");
            }
            args.put("sheriffName", sheriffName);
        }
        if (!signedIn) {
            query.append(" AND (e.viewPrincipal IS NULL OR e.viewPrincipal = 'public')");
        }
        query.append('\n');
        query.append("OPTIONAL MATCH (o)-[a:AVATAR]->(mf:MediaFile)\n");
        query.append("OPTIONAL MATCH (e)-[md:MEDIA_PREVIEW]->(mp:MediaFile)\n");
        if (after != null) {
            query.append("ORDER BY e.moment ASC\n");
        } else if (before != null) {
            query.append("ORDER BY e.moment DESC\n");
        }
        query.append("LIMIT $limit\n");
        args.put("limit", limit);
        query.append(
            """
            RETURN
                n.name AS nodeName,
            """
        );
        query.append(
            switch (entryType) {
                case ALL ->
                    """
                        CASE
                            WHEN size(p) = 0 THEN e.id
                            ELSE p[0].id
                        END AS postingId,
                        CASE
                            WHEN size(p) = 0 THEN NULL
                            ELSE e.id
                        END AS commentId,
                    """;
                case POSTING ->
                    """
                        e.id AS postingId,
                        NULL AS commentId,
                    """;
                case COMMENT ->
                    """
                        p.id AS postingId,
                        e.id AS commentId,
                    """;
            }
        );
        query.append(
            """
                o AS owner,
                mf AS avatar,
                a.shape AS avatarShape,
                e AS entry,
                mp AS mediaPreview,
                md.mediaId AS mediaPreviewId
            """
        );

        return database.tx().run(query.toString(), args).list(this::buildSearchResult);
    }

    public List<SearchEntryInfo> findDocuments(
        SearchEntryType entryType, List<String> documentIds, String sheriffName
    ) {
        if (documentIds.isEmpty()) {
            return Collections.emptyList();
        }

        var query = new StringBuilder();
        var args = new HashMap<String, Object>();

        query.append("UNWIND $ids AS id\n");
        args.put("ids", documentIds);
        query.append("MATCH ");
        query.append(
            switch (entryType) {
                case ALL -> "(e:Entry {documentId: id})(()-[:UNDER]->(p:Posting)){0,1}()";
                case POSTING -> "(e:Posting {documentId: id})";
                case COMMENT -> "(e:Comment {documentId: id})-[:UNDER]->(p:Posting)";
            }
        );
        query.append("-[:SOURCE]->(n:MoeraNode), (e)-[:OWNER]->(o:MoeraNode)\n");
        if (sheriffName != null) {
            query.append("WHERE (n.sheriffMarks IS NULL OR NOT ($sheriffName IN n.sheriffMarks))");
            query.append(" AND (o.ownerSheriffMarks IS NULL OR NOT ($sheriffName IN o.ownerSheriffMarks))");
            query.append(" AND (e.sheriffMarks IS NULL OR NOT ($sheriffName IN e.sheriffMarks))");
            if (entryType == SearchEntryType.ALL) {
                query.append(
                    " AND (size(p) = 0 OR p[0].sheriffMarks IS NULL OR NOT ($sheriffName IN p[0].sheriffMarks))"
                );
            } else if (entryType == SearchEntryType.COMMENT) {
                query.append(" AND (p.sheriffMarks IS NULL OR NOT ($sheriffName IN p.sheriffMarks))");
            }
            query.append('\n');
            args.put("sheriffName", sheriffName);
        }
        query.append("OPTIONAL MATCH (o)-[a:AVATAR]->(mf:MediaFile)\n");
        query.append("OPTIONAL MATCH (e)-[md:MEDIA_PREVIEW]->(mp:MediaFile)\n");
        query.append(
            """
            RETURN
                n.name AS nodeName,
            """
        );
        query.append(
            switch (entryType) {
                case ALL ->
                    """
                        CASE
                            WHEN size(p) = 0 THEN e.id
                            ELSE p[0].id
                        END AS postingId,
                        CASE
                            WHEN size(p) = 0 THEN NULL
                            ELSE e.id
                        END AS commentId,
                    """;
                case POSTING ->
                    """
                        e.id AS postingId,
                        NULL AS commentId,
                    """;
                case COMMENT ->
                    """
                        p.id AS postingId,
                        e.id AS commentId,
                    """;
            }
        );
        query.append(
            """
                o AS owner,
                mf AS avatar,
                a.shape AS avatarShape,
                e AS entry,
                mp AS mediaPreview,
                md.mediaId AS mediaPreviewId
            """
        );

        return database.tx().run(query.toString(), args).list(this::buildSearchResult);
    }

    /*
     * Record fields:
     *
     * String nodeName
     * String postingId
     * String commentId (optional)
     * Node owner
     * Node avatar (optional)
     * String avatarShape (optional)
     * Node entry
     */
    private SearchEntryInfo buildSearchResult(org.neo4j.driver.Record r) {
        var info = new SearchEntryInfo();
        info.setNodeName(r.get("nodeName").asString());
        info.setPostingId(r.get("postingId").asString(null));
        info.setCommentId(r.get("commentId").asString(null));
        var owner = r.get("owner").asNode();
        info.setOwnerName(owner.get("name").asString(null));
        info.setOwnerFullName(owner.get("fullName").asString(null));
        var avatarShape = r.get("avatarShape").asString(null);
        var avatar = r.get("avatar").isNull() ? null : new MediaFile(r.get("avatar").asNode());
        if (avatar != null) {
            info.setOwnerAvatar(AvatarImageUtil.build(avatar, avatarShape));
        }
        var entry = r.get("entry").asNode();
        info.setBodyPreview(new Body(entry.get("bodyPreview").asString()));
        info.setHeading(entry.get("heading").asString(null));
        info.setImageCount(entry.get("imageCount").asInt(0));
        var mediaPreview = r.get("mediaPreview").isNull() ? null : new MediaFile(r.get("mediaPreview").asNode());
        if (mediaPreview != null) {
            info.setMediaPreview(PublicMediaFileInfoUtil.build(mediaPreview));
        }
        info.setMediaPreviewId(r.get("mediaPreviewId").asString(null));
        info.setVideoPresent(entry.get("videoPresent").asBoolean(false));
        var repliedTo = entry.get("repliedTo").asString(null);
        if (repliedTo != null) {
            try {
                info.setRepliedTo(objectMapper.readValue(repliedTo, SearchRepliedTo.class));
            } catch (JsonProcessingException e) {
                log.error("Cannot deserialize repliedTo", e);
            }
        }
        info.setCreatedAt(entry.get("createdAt").asLong(0));
        var viewPrincipal = entry.get("viewPrincipal").asString(null);
        if (!Principal.PUBLIC.getValue().equals(viewPrincipal)) {
            var operations = new SearchEntryOperations();
            operations.setView(new Principal(viewPrincipal));
            info.setOperations(operations);
        }
        info.setMoment(entry.get("moment").asLong(0));
        return info;
    }

}
