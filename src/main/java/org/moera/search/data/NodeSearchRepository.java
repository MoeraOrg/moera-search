package org.moera.search.data;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.moera.lib.node.types.RecommendedNodeInfo;
import org.moera.lib.node.types.SearchNodeInfo;
import org.moera.search.api.model.RecommendedNodeInfoUtil;
import org.moera.search.api.model.SearchNodeInfoUtil;
import org.moera.search.util.Util;
import org.springframework.stereotype.Component;

@Component
public class NodeSearchRepository {

    private static final String SHERIFF_FILTER =
        """
        (
            $sheriffName IS NULL
            OR
                (n.sheriffMarks IS NULL OR NOT ($sheriffName IN n.sheriffMarks))
                AND (n.ownerSheriffMarks IS NULL OR NOT ($sheriffName IN n.ownerSheriffMarks))
        )
        """;

    @Inject
    private Database database;

    public List<SearchNodeInfo> searchClose(String clientName, String sheriffName, int limit) {
        var args = new HashMap<String, Object>();
        args.put("clientName", clientName);
        args.put("sheriffName", sheriffName);
        args.put("limit", limit);

        return database.tx().run(
            """
            MATCH (:MoeraNode {name: $clientName})-[c:CLOSE_TO]->(n:MoeraNode)
            WHERE
            """
                + SHERIFF_FILTER
            + """
            WITH n, c.distance AS distance
            ORDER BY distance ASC
            LIMIT $limit
            OPTIONAL MATCH (n)-[a:AVATAR]->(mf:MediaFile)
            RETURN n, distance, a.shape AS shape, mf
            """,
            args
        ).stream().map(r -> {
            var node = r.get("n").asNode();
            var distance = r.get("distance").asFloat();
            var avatarShape = r.get("shape").asString(null);
            var avatar = r.get("mf").isNull() ? null : new MediaFile(r.get("mf").asNode());
            return SearchNodeInfoUtil.build(node, avatar, avatarShape, distance);
        }).toList();
    }

    public int countCloseByNamePrefix(String clientName, String prefix, String sheriffName) {
        var args = new HashMap<String, Object>();
        args.put("clientName", clientName);
        args.put("prefix", prefix.toLowerCase());
        args.put("sheriffName", sheriffName);

        return database.tx().run(
            """
            MATCH (:MoeraNode {name: $clientName})-[:CLOSE_TO]->(n:MoeraNode)
            WHERE lower(n.name) STARTS WITH $prefix AND
            """
                + SHERIFF_FILTER
            + """
            RETURN count(n) AS total
            """,
            args
        ).single().get("total").asInt(0);
    }

    public List<SearchNodeInfo> searchCloseByNamePrefix(
        String clientName, String prefix, String sheriffName, int offset, int limit
    ) {
        var args = new HashMap<String, Object>();
        args.put("clientName", clientName);
        args.put("prefix", prefix.toLowerCase());
        args.put("sheriffName", sheriffName);
        args.put("offset", offset);
        args.put("limit", limit);

        return database.tx().run(
            """
            MATCH (:MoeraNode {name: $clientName})-[c:CLOSE_TO]->(n:MoeraNode)
            WHERE lower(n.name) STARTS WITH $prefix AND
            """
                + SHERIFF_FILTER
            + """
            WITH n, c.distance AS distance
            ORDER BY distance ASC
            OFFSET $offset
            LIMIT $limit
            OPTIONAL MATCH (n)-[a:AVATAR]->(mf:MediaFile)
            RETURN n, distance, a.shape AS shape, mf
            """,
            args
        ).stream().map(r -> {
            var node = r.get("n").asNode();
            var distance = r.get("distance").asFloat();
            var avatarShape = r.get("shape").asString(null);
            var avatar = r.get("mf").isNull() ? null : new MediaFile(r.get("mf").asNode());
            return SearchNodeInfoUtil.build(node, avatar, avatarShape, distance);
        }).toList();
    }

    public int countCloseByFullNamePrefix(String clientName, String prefix, String sheriffName) {
        var args = new HashMap<String, Object>();
        args.put("clientName", clientName);
        args.put("prefix", prefixRegex(prefix));
        args.put("sheriffName", sheriffName);

        return database.tx().run(
            """
            MATCH (:MoeraNode {name: $clientName})-[:CLOSE_TO]->(n:MoeraNode)
            WHERE n.fullName =~ $prefix AND
            """
                + SHERIFF_FILTER
            + """
            RETURN count(n) AS total
            """,
            args
        ).single().get("total").asInt(0);
    }

    public List<SearchNodeInfo> searchCloseByFullNamePrefix(
        String clientName, String prefix, String sheriffName, int offset, int limit
    ) {
        var args = new HashMap<String, Object>();
        args.put("clientName", clientName);
        args.put("prefix", prefixRegex(prefix));
        args.put("sheriffName", sheriffName);
        args.put("offset", offset);
        args.put("limit", limit);

        return database.tx().run(
            """
            MATCH (:MoeraNode {name: $clientName})-[c:CLOSE_TO]->(n:MoeraNode)
            WHERE n.fullName =~ $prefix AND
            """
                + SHERIFF_FILTER
            + """
            WITH n, c.distance AS distance
            ORDER BY c.distance ASC
            OFFSET $offset
            LIMIT $limit
            OPTIONAL MATCH (n)-[a:AVATAR]->(mf:MediaFile)
            RETURN n, distance, a.shape AS shape, mf
            """,
            args
        ).stream().map(r -> {
            var node = r.get("n").asNode();
            var distance = r.get("distance").asFloat();
            var avatarShape = r.get("shape").asString(null);
            var avatar = r.get("mf").isNull() ? null : new MediaFile(r.get("mf").asNode());
            return SearchNodeInfoUtil.build(node, avatar, avatarShape, distance);
        }).toList();
    }

    private String prefixRegex(String prefix) {
        StringBuilder buf = new StringBuilder("(?i)");

        var terms = prefix.split("\\s+", 3);
        for (int n0 = 0; n0 < terms.length; n0++) {
            if (terms.length == 1) {
                buf.append(termsRegex(terms, n0, -1, -1));
                continue;
            }

            for (int n1 = 0; n1 < terms.length; n1++) {
                if (n1 == n0) {
                    continue;
                }
                if (terms.length == 2) {
                    buf.append(termsRegex(terms, n0, n1, -1));
                    continue;
                }

                for (int n2 = 0; n2 < terms.length; n2++) {
                    if (n2 == n1 || n2 == n0) {
                        continue;
                    }
                    buf.append(termsRegex(terms, n0, n1, n2));
                }
            }
        }

        return buf.substring(0, buf.length() - 1);
    }

    private String termsRegex(String[] terms, int n0, int n1, int n2) {
        StringBuilder buf = new StringBuilder("(?:.*");
        if (n0 >= 0) {
            buf.append(termRegex(terms[n0]));
        }
        if (n1 >= 0) {
            buf.append(termRegex(terms[n1]));
        }
        if (n2 >= 0) {
            buf.append(termRegex(terms[n2]));
        }
        buf.append(")|");
        return buf.toString();
    }

    private String termRegex(String term) {
        return "\\b" + Pattern.quote(term) + ".*";
    }

    public int countByNamePrefix(String prefix, String sheriffName) {
        var args = new HashMap<String, Object>();
        args.put("prefix", prefix.toLowerCase());
        args.put("sheriffName", sheriffName);

        return database.tx().run(
            """
            MATCH (n:MoeraNode)
            WHERE lower(n.name) STARTS WITH $prefix AND
            """
                + SHERIFF_FILTER
            + """
            RETURN count(n) AS total
            """,
            args
        ).single().get("total").asInt(0);
    }

    public int countBlockedByNamePrefix(String clientName, String prefix, String sheriffName) {
        var args = new HashMap<String, Object>();
        args.put("clientName", clientName);
        args.put("prefix", prefix.toLowerCase());
        args.put("sheriffName", sheriffName);

        return database.tx().run(
            """
            MATCH (n:MoeraNode)
            WHERE lower(n.name) STARTS WITH $prefix AND
            """
                + SHERIFF_FILTER
            + """
                AND EXISTS((n)<-[:BLOCKS]-(:MoeraNode {name: $clientName}))
            RETURN count(n) AS total
            """,
            args
        ).single().get("total").asInt(0);
    }

    public List<SearchNodeInfo> searchByNamePrefix(
        String clientName, String prefix, String sheriffName, int offset, int limit, boolean blocked
    ) {
        var args = new HashMap<String, Object>();
        args.put("clientName", clientName);
        args.put("prefix", prefix.toLowerCase());
        args.put("sheriffName", sheriffName);
        args.put("offset", offset);
        args.put("limit", limit);
        args.put("blocked", blocked);

        return database.tx().run(
            """
            MATCH (n:MoeraNode)
            WHERE lower(n.name) STARTS WITH $prefix
            WITH
                n,
                CASE
                    WHEN $clientName IS NULL THEN false
                    ELSE EXISTS((n)<-[:BLOCKS]-(:MoeraNode {name: $clientName}))
                END AS blocked
            WHERE blocked = $blocked AND
            """
                + SHERIFF_FILTER
            + """
            OFFSET $offset
            LIMIT $limit
            OPTIONAL MATCH (n)-[a:AVATAR]->(mf:MediaFile)
            RETURN n, a.shape AS shape, mf
            """,
            args
        ).stream().map(r -> {
            var node = r.get("n").asNode();
            var avatarShape = r.get("shape").asString(null);
            var avatar = r.get("mf").isNull() ? null : new MediaFile(r.get("mf").asNode());
            return SearchNodeInfoUtil.build(node, avatar, avatarShape, blocked);
        }).toList();
    }

    private String fullNameQuery(String prefix) {
        var terms = Util.escapeLucene(prefix).split("\\s+");
        return Arrays.stream(terms).map(t -> t + "*").collect(Collectors.joining(" AND "));
    }

    public int countByFullNamePrefix(String prefix, String sheriffName) {
        var args = new HashMap<String, Object>();
        args.put("query", fullNameQuery(prefix));
        args.put("sheriffName", sheriffName);

        return database.tx().run(
            """
            CALL db.index.fulltext.queryNodes("moera_node_full_name", $query) YIELD node AS n, score
            WHERE
            """
                + SHERIFF_FILTER
            + """
            RETURN count(n) AS total
            """,
            args
        ).single().get("total").asInt(0);
    }

    public int countBlockedByFullNamePrefix(String clientName, String prefix, String sheriffName) {
        var args = new HashMap<String, Object>();
        args.put("clientName", clientName);
        args.put("query", fullNameQuery(prefix));
        args.put("sheriffName", sheriffName);

        return database.tx().run(
            """
            CALL db.index.fulltext.queryNodes("moera_node_full_name", $query) YIELD node AS n, score
            WHERE
            """
                + SHERIFF_FILTER
            + """
                AND EXISTS((n)<-[:BLOCKS]-(:MoeraNode {name: $clientName}))
            RETURN count(n) AS total
            """,
            args
        ).single().get("total").asInt(0);
    }

    public List<SearchNodeInfo> searchByFullNamePrefix(
        String clientName, String prefix, String sheriffName, int offset, int limit, boolean blocked
    ) {
        var args = new HashMap<String, Object>();
        args.put("clientName", clientName);
        args.put("query", fullNameQuery(prefix));
        args.put("sheriffName", sheriffName);
        args.put("offset", offset);
        args.put("limit", limit);
        args.put("blocked", blocked);

        return database.tx().run(
            """
            CALL db.index.fulltext.queryNodes("moera_node_full_name", $query) YIELD node AS n, score
            WITH
                n,
                CASE
                    WHEN $clientName IS NULL THEN false
                    ELSE EXISTS((n)<-[:BLOCKS]-(:MoeraNode {name: $clientName}))
                END AS blocked
            WHERE blocked = $blocked AND
            """
                + SHERIFF_FILTER
            + """
            OFFSET $offset
            LIMIT $limit
            OPTIONAL MATCH (n)-[a:AVATAR]->(mf:MediaFile)
            RETURN n, a.shape AS shape, mf
            """,
            args
        ).stream().map(r -> {
            var node = r.get("n").asNode();
            var avatarShape = r.get("shape").asString(null);
            var avatar = r.get("mf").isNull() ? null : new MediaFile(r.get("mf").asNode());
            return SearchNodeInfoUtil.build(node, avatar, avatarShape, blocked);
        }).toList();
    }

    public List<SearchNodeInfo> searchDefault(String sheriffName, int limit) {
        var args = new HashMap<String, Object>();
        args.put("sheriffName", sheriffName);
        args.put("limit", limit);

        return database.tx().run(
            """
            MATCH (n:MoeraNode)
            WHERE n.activity IS NOT NULL AND
            """
                + SHERIFF_FILTER
                + """
            ORDER BY n.activity DESC
            LIMIT $limit
            OPTIONAL MATCH (n)-[a:AVATAR]->(mf:MediaFile)
            RETURN
                n,
                a.shape AS shape,
                mf
            """,
            args
        ).stream().map(r -> {
            var node = r.get("n").asNode();
            var avatarShape = r.get("shape").asString(null);
            var avatar = r.get("mf").isNull() ? null : new MediaFile(r.get("mf").asNode());
            return SearchNodeInfoUtil.build(node, avatar, avatarShape, false);
        }).toList();
    }

    public List<RecommendedNodeInfo> searchActive(String sheriffName, int limit) {
        var args = new HashMap<String, Object>();
        args.put("sheriffName", sheriffName);
        args.put("limit", limit);

        return database.tx().run(
            """
            MATCH (n:MoeraNode)
            WHERE n.activity IS NOT NULL AND
            """
                + SHERIFF_FILTER
            + """
            ORDER BY n.activity DESC
            LIMIT $limit
            OPTIONAL MATCH (n)-[a:AVATAR]->(mf:MediaFile)
            RETURN
                n,
                a.shape AS shape,
                mf,
                COUNT {(n)<-[:SUBSCRIBED {feedName: "timeline"}]-(s:MoeraNode)} AS subscribers,
                COUNT {(n)<-[:PUBLISHED_IN]-(pb:Publication {feedName: "timeline"})} AS postings
            """,
            args
        ).stream().map(r -> {
            var node = r.get("n").asNode();
            var avatarShape = r.get("shape").asString(null);
            var avatar = r.get("mf").isNull() ? null : new MediaFile(r.get("mf").asNode());
            var subscribers = r.get("subscribers").asInt(0);
            var postings = r.get("postings").asInt(0);
            return RecommendedNodeInfoUtil.build(node, avatar, avatarShape, subscribers, postings);
        }).toList();
    }

}
