CREATE INDEX posting_scan_reactions IF NOT EXISTS FOR (p:Posting) ON (p.scanReactions, p.reactionsScannedAt);
CREATE INDEX comment_scan_reactions IF NOT EXISTS FOR (c:Comment) ON (c.scanReactions, c.reactionsScannedAt);
