MATCH (p:Posting)
WITH
    p,
    COUNT {(p)<-[:REACTS_TO]-(:Reaction {negative: false})} AS r,
    COUNT {(p)<-[:UNDER]-(:Comment)} AS c
SET p.recommendationOrder = p.createdAt + toInteger(apoc.math.tanh((r + c * 5) / 35.0) * 600000);
CREATE INDEX posting_recommendation_order IF NOT EXISTS FOR (p:Posting) ON (p.recommendationOrder);
