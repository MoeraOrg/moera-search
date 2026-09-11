package org.moera.search.data;

final class RecommendationOrder {

    // TODO take publications in timelines of other users into account
    static final String SET =
        """
        WITH
            p,
            n,
            COUNT {(p)<-[:REACTS_TO]-(:Reaction {negative: false})} AS r,
            COUNT {(p)<-[:UNDER]-(:Comment)} AS c,
            COUNT {(p)<-[:DONE_TO]-(:Favor)-[:CAUSED_BY]->(:Onboarding)} AS ob,
            CASE WHEN n.sourceUri IS NOT NULL AND n.sourceUri <> "" THEN 25 ELSE 0 END AS ext
        SET p.recommendationOrder =
            p.createdAt + toInteger(apoc.math.tanh((r + c * 5 + ob * 25 - ext) / 35.0) * 600000);
        """;

    private RecommendationOrder() {
    }

}
