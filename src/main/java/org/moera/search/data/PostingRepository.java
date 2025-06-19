package org.moera.search.data;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.inject.Inject;

import org.moera.lib.node.types.PostingInfo;
import org.moera.lib.node.types.PostingOperations;
import org.moera.lib.node.types.RecommendedPostingInfo;
import org.moera.lib.node.types.body.Body;
import org.moera.lib.node.types.principal.Principal;
import org.moera.search.api.model.AvatarImageUtil;
import org.moera.search.util.BodyUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Component
public class PostingRepository {

    private static final String RETURN_RECOMMENDATIONS =
        """
        MATCH (p)-[:SOURCE]->(n:MoeraNode), (p)-[:OWNER]->(o:MoeraNode)
        WHERE
            $sheriffName IS NULL
            OR
                (n.sheriffMarks IS NULL OR NOT ($sheriffName IN n.sheriffMarks))
                AND (o.ownerSheriffMarks IS NULL OR NOT ($sheriffName IN o.ownerSheriffMarks))
                AND (p.sheriffMarks IS NULL OR NOT ($sheriffName IN p.sheriffMarks))
        LIMIT $limit
        OPTIONAL MATCH (o)-[a:AVATAR]->(mf:MediaFile)
        RETURN
            n.name AS nodeName,
            p.id AS postingId,
            o AS owner,
            mf AS avatar,
            a.shape AS avatarShape,
            p.heading AS heading,
            COUNT {(p)<-[:REACTS_TO]-(:Reaction {negative: false})} AS totalReactions,
            COUNT {
                (p)<-[:REACTS_TO]-(r:Reaction WHERE r.negative = false AND r.createdAt > $yesterday)
            } AS dayReactions,
            COUNT {(p)<-[:UNDER]-(:Comment)} AS totalComments,
            COUNT {(p)<-[:UNDER]-(c:Comment WHERE c.createdAt > $yesterday)} AS dayComments
        """;

    @Inject
    private Database database;

    public boolean exists(String nodeName, String postingId) {
        return database.tx().run(
            """
            RETURN EXISTS {
                MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})
            } AS e
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        ).single().get("e").asBoolean();
    }

    public void createPosting(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (n:MoeraNode {name: $nodeName})
            MERGE (n)<-[:SOURCE]-(p:Posting:Entry {id: $postingId})
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

    public void deletePosting(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            DETACH DELETE p
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

    public void assignPostingOwner(String nodeName, String postingId, String ownerName) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId}),
                  (o:MoeraNode {name: $ownerName})
            MERGE (o)<-[:OWNER]-(p)
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "ownerName", ownerName
            )
        );
    }

    public void fillPosting(String nodeName, String postingId, PostingInfo info) {
        var args = new HashMap<String, Object>();
        args.put("nodeName", nodeName);
        args.put("postingId", postingId);
        args.put("revisionId", info.getRevisionId());
        args.put("ownerFullName", info.getOwnerFullName());
        args.put("heading", info.getHeading());
        var bodyPreview = new Body();
        if (info.getBodyPreview() != null && !info.getBodyPreview().getEncoded().equals(Body.EMPTY)) {
            bodyPreview.setText(info.getBodyPreview().getText());
            bodyPreview.setSubject(info.getBodyPreview().getSubject());
            if (ObjectUtils.isEmpty(bodyPreview.getSubject())) {
                bodyPreview.setSubject(info.getBody().getSubject());
            }
        } else {
            bodyPreview.setText(info.getBody().getText());
            bodyPreview.setSubject(info.getBody().getSubject());
        }
        args.put("bodyPreview", bodyPreview.getEncoded());
        var counts = BodyUtil.countBodyMedia(info.getBody(), info.getMedia());
        args.put("imageCount", counts.imageCount());
        args.put("videoPresent", counts.videoPresent());
        args.put("createdAt", info.getCreatedAt());
        args.put("editedAt", info.getEditedAt());
        args.put("viewPrincipal", PostingOperations.getView(info.getOperations(), Principal.PUBLIC).getValue());
        args.put("now", Instant.now().toEpochMilli());

        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            SET p.revisionId = $revisionId,
                p.ownerFullName = $ownerFullName,
                p.heading = $heading,
                p.bodyPreview = $bodyPreview,
                p.imageCount = $imageCount,
                p.videoPresent = $videoPresent,
                p.createdAt = $createdAt,
                p.editedAt = $editedAt,
                p.viewPrincipal = $viewPrincipal,
                p.scan = true,
                p.scannedAt = $now
            """,
            args
        );
    }

    public void setHeading(String nodeName, String postingId, String heading) {
        var args = new HashMap<String, Object>();
        args.put("nodeName", nodeName);
        args.put("postingId", postingId);
        args.put("heading", heading);

        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            SET p.heading = $heading
            """,
            args
        );
    }

    public void addAvatar(String nodeName, String postingId, String mediaFileId, String shape) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId}),
                  (mf:MediaFile {id: $mediaFileId})
            CREATE (p)-[:AVATAR {shape: $shape}]->(mf)
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "mediaFileId", mediaFileId,
                "shape", shape
            )
        );
    }

    public void removeAvatar(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})-[a:AVATAR]->()
            DELETE a
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

    public EntryRevision getRevision(String nodeName, String postingId) {
        var r = database.tx().run(
            """
            OPTIONAL MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            RETURN p.revisionId AS revisionId, p.viewPrincipal AS viewPrincipal
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        ).single();

        return new EntryRevision(
            r.get("revisionId").asString(null),
            r.get("viewPrincipal").asString(Principal.PUBLIC.getValue())
        );
    }

    public String getDocumentId(String nodeName, String postingId) {
        return database.tx().run(
            """
            OPTIONAL MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            RETURN p.documentId AS id
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        ).single().get("id").asString(null);
    }

    public void setDocumentId(String nodeName, String postingId, String documentId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            SET p.documentId = $documentId
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "documentId", documentId
            )
        );
    }

    public void sheriffMark(String sheriffName, String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            WHERE p.sheriffMarks IS NULL OR NOT ($sheriffName IN p.sheriffMarks)
            SET p.sheriffMarks = CASE
                WHEN p.sheriffMarks IS NULL THEN [$sheriffName]
                ELSE p.sheriffMarks + [$sheriffName]
            END
            """,
            Map.of(
                "sheriffName", sheriffName,
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

    public void sheriffUnmark(String sheriffName, String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            WHERE p.sheriffMarks IS NOT NULL AND $sheriffName IN p.sheriffMarks
            SET p.sheriffMarks = [mark IN p.sheriffMarks WHERE mark <> $sheriffName]
            """,
            Map.of(
                "sheriffName", sheriffName,
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

    public void addMediaPreview(String nodeName, String postingId, String mediaId, String mediaFileId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId}),
                  (mf:MediaFile {id: $mediaFileId})
            CREATE (p)-[:MEDIA_PREVIEW {mediaId: $mediaId}]->(mf)
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "mediaId", mediaId,
                "mediaFileId", mediaFileId
            )
        );
    }

    public void removeMediaPreview(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})-[mp:MEDIA_PREVIEW]->()
            DELETE mp
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

    public String getMediaPreviewId(String nodeName, String postingId) {
        return database.tx().run(
            """
            OPTIONAL MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(:Posting {id: $postingId})-[mp:MEDIA_PREVIEW]->()
            RETURN mp.mediaId AS mediaId
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        ).single().get("mediaId").asString(null);
    }

    public void scanSucceeded(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            SET p.scan = true, p.scannedAt = $now
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public void scanFailed(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            SET p.scan = false, p.scannedAt = $now
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public boolean isCommentsScanned(String nodeName, String postingId) {
        return database.tx().run(
            """
            OPTIONAL MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            RETURN p.scanComments IS NOT NULL AS scanned
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        ).single().get("scanned").asBoolean();
    }

    public void scanCommentsSucceeded(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            SET p.scanComments = true, p.commentsScannedAt = $now
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public void scanCommentsFailed(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            SET p.scanComments = false, p.commentsScannedAt = $now
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public boolean isReactionsScanned(String nodeName, String postingId) {
        return database.tx().run(
            """
            OPTIONAL MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            RETURN p.scanReactions IS NOT NULL AS scanned
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        ).single().get("scanned").asBoolean();
    }

    public void scanReactionsSucceeded(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            SET p.scanReactions = true, p.reactionsScannedAt = $now
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    public void scanReactionsFailed(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            SET p.scanReactions = false, p.reactionsScannedAt = $now
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId,
                "now", Instant.now().toEpochMilli()
            )
        );
    }

    private List<RecommendedPostingInfo> findPopularByField(String fieldName, String sheriffName, int limit) {
        var args = new HashMap<String, Object>();
        args.put("sheriffName", sheriffName);
        args.put("yesterday", Instant.now().minus(1, ChronoUnit.DAYS).getEpochSecond());
        args.put("limit", limit);

        return database.tx().run(
            """
            MATCH (p:Posting)
            ORDER BY p.%1$s DESC
            WITH p
            WHERE p.%1$s IS NOT NULL AND p.%1$s > 0
            """.formatted(fieldName) + RETURN_RECOMMENDATIONS,
            args
        ).list(this::buildRecommendedPosting);
    }

    public List<RecommendedPostingInfo> findPopular(String sheriffName, int limit) {
        return findPopularByField("popularity", sheriffName, limit);
    }

    public List<RecommendedPostingInfo> findReadPopular(String sheriffName, int limit) {
        return findPopularByField("readPopularity", sheriffName, limit);
    }

    public List<RecommendedPostingInfo> findCommentPopular(String sheriffName, int limit) {
        return findPopularByField("commentPopularity", sheriffName, limit);
    }

    public List<RecommendedPostingInfo> findRecommended(String clientName, String sheriffName, int limit) {
        var args = new HashMap<String, Object>();
        args.put("clientName", clientName);
        args.put("sheriffName", sheriffName);
        args.put("yesterday", Instant.now().minus(1, ChronoUnit.DAYS).getEpochSecond());
        args.put("limit", limit);

        return database.tx().run(
            """
            MATCH (c:MoeraNode {name: $clientName})-[:SUBSCRIBED|FRIEND]->(fr:MoeraNode)
                  <-[:DONE_BY]-(:Favor)-[:DONE_TO]->(p:Posting)-[:SOURCE]->(n:MoeraNode)
            WHERE
                NOT EXISTS { MATCH (c)-[:SUBSCRIBED|BLOCKS|DONT_RECOMMEND]->(n) }
                AND NOT EXISTS { MATCH (c)-[:WAS_RECOMMENDED|DONT_RECOMMEND]->(p) }
                AND NOT EXISTS { MATCH (c)<-[:PUBLISHED_IN]-(:Publication {feedName: "timeline"})-[:CONTAINS]->(p) }
            ORDER BY p.recommendationOrder DESC
            """ + RETURN_RECOMMENDATIONS,
            args
        ).list(this::buildRecommendedPosting);
    }

    public List<RecommendedPostingInfo> findRecommendedByNobody(String clientName, String sheriffName, int limit) {
        var args = new HashMap<String, Object>();
        args.put("clientName", clientName);
        args.put("sheriffName", sheriffName);
        args.put("yesterday", Instant.now().minus(1, ChronoUnit.DAYS).getEpochSecond());
        args.put("limit", limit);

        return database.tx().run(
            """
            MATCH (c:MoeraNode {name: $clientName}), (p:Posting)-[:SOURCE]->(n:MoeraNode)
            WHERE
                NOT EXISTS { MATCH (c)-[:SUBSCRIBED|BLOCKS|DONT_RECOMMEND]->(n) }
                AND NOT EXISTS { MATCH (c)-[:WAS_RECOMMENDED|DONT_RECOMMEND]->(p) }
                AND NOT EXISTS { MATCH (c)<-[:PUBLISHED_IN]-(:Publication {feedName: "timeline"})-[:CONTAINS]->(p) }
            ORDER BY p.popularity IS NOT NULL DESC, p.popularity DESC, p.recommendationOrder DESC
            """ + RETURN_RECOMMENDATIONS,
            args
        ).list(this::buildRecommendedPosting);
    }

    /*
     * Record fields:
     *
     * String nodeName
     * String postingId
     * Node owner
     * Node avatar (optional)
     * String avatarShape (optional)
     * String heading (optional)
     * int totalReactions
     * int dayReactions
     * int totalComments
     * int dayComments
     */
    private RecommendedPostingInfo buildRecommendedPosting(org.neo4j.driver.Record r) {
        var info = new RecommendedPostingInfo();
        info.setNodeName(r.get("nodeName").asString());
        info.setPostingId(r.get("postingId").asString(null));
        var owner = r.get("owner").asNode();
        info.setOwnerName(owner.get("name").asString(null));
        info.setOwnerFullName(owner.get("fullName").asString(null));
        var avatarShape = r.get("avatarShape").asString(null);
        var avatar = r.get("avatar").isNull() ? null : new MediaFile(r.get("avatar").asNode());
        if (avatar != null) {
            info.setOwnerAvatar(AvatarImageUtil.build(avatar, avatarShape));
        }
        info.setHeading(r.get("heading").asString(null));
        info.setTotalPositiveReactions(r.get("totalReactions").asInt(0));
        info.setLastDayPositiveReactions(r.get("dayReactions").asInt(0));
        info.setTotalComments(r.get("totalComments").asInt(0));
        info.setLastDayComments(r.get("dayComments").asInt(0));
        return info;
    }

    public void refreshReadPopularity() {
        database.tx().run(
            """
            MATCH (p:Posting)<-[:DONE_TO]-(f:Favor)-[:CAUSED_BY]->(:Publication|Reaction)
            WITH p, f.value * (1.0 - (toFloat($now - f.createdAt) / 3600000 / f.decayHours)^2) AS rest
            WITH p, sum(rest) AS popularity
            SET p.readPopularity = popularity
            """,
            Map.of("now", Instant.now().toEpochMilli())
        );
    }

    public void refreshCommentPopularity() {
        database.tx().run(
            """
            MATCH (p:Posting)<-[:DONE_TO]-(f:Favor)-[:CAUSED_BY]->(:Comment)
            WITH p, f.value * (1.0 - (toFloat($now - f.createdAt) / 3600000 / f.decayHours)^2) AS rest
            WITH p, sum(rest) AS popularity
            SET p.commentPopularity = popularity
            """,
            Map.of("now", Instant.now().toEpochMilli())
        );
    }

    public void refreshPopularity() {
        database.tx().run(
            """
            MATCH (
                p:Posting
                WHERE p.readPopularity IS NOT NULL AND p.readPopularity > 0
                    OR p.commentPopularity IS NOT NULL AND p.commentPopularity > 0
            )
            SET p.popularity = coalesce(p.readPopularity, 0.0) + coalesce(p.commentPopularity, 0.0)
            """,
            Map.of("now", Instant.now().toEpochMilli())
        );
    }

    // TODO take publications in timelines of other users into account
    public void updateRecommendationOrder(String nodeName, String postingId) {
        database.tx().run(
            """
            MATCH (:MoeraNode {name: $nodeName})<-[:SOURCE]-(p:Posting {id: $postingId})
            WITH
                p,
                COUNT {(p)<-[:REACTS_TO]-(:Reaction {negative: false})} AS r,
                COUNT {(p)<-[:UNDER]-(:Comment)} AS c
            SET p.recommendationOrder = p.createdAt + toInteger(apoc.math.tanh((r + c * 5) / 35.0) * 600000);
            """,
            Map.of(
                "nodeName", nodeName,
                "postingId", postingId
            )
        );
    }

}
