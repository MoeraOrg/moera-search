MERGE (:Upgrade);
MATCH (p:Posting), (u:Upgrade)
CREATE (p)<-[:RESCAN]-(u);
MATCH (c:Comment WHERE c.imageCount > 0), (u:Upgrade)
CREATE (c)<-[:RESCAN]-(u);
