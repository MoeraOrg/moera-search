DROP INDEX published_published_at IF EXISTS;
CREATE INDEX publication_published_at IF NOT EXISTS FOR (pb:Publication) ON (pb.publishedAt);
