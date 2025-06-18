CREATE INDEX posting_read_popularity IF NOT EXISTS FOR (p:Posting) ON (p.readPopularity);
CREATE INDEX posting_comment_popularity IF NOT EXISTS FOR (p:Posting) ON (p.commentPopularity);
CREATE INDEX posting_popularity IF NOT EXISTS FOR (p:Posting) ON (p.popularity);
