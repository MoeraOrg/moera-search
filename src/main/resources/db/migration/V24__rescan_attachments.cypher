MERGE (:Upgrade);
MATCH (e:Entry WHERE e.imageCount > 0), (u:Upgrade)
CREATE (e)<-[:RESCAN]-(u);
