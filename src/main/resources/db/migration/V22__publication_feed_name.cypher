CREATE INDEX publication_feed_name IF NOT EXISTS FOR (pb:Publication) ON (pb.feedName);
