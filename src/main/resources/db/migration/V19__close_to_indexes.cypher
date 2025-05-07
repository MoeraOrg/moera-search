MATCH ()-[c:CLOSE_TO]->() SET c.updatedAt = 0;
CREATE INDEX close_to_distance IF NOT EXISTS FOR ()-[c:CLOSE_TO]->() ON (c.distance);
CREATE INDEX close_to_updated_at IF NOT EXISTS FOR ()-[c:CLOSE_TO]->() ON (c.updatedAt);
