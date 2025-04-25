CREATE INDEX posting_scan_comments IF NOT EXISTS FOR (p:Posting) ON (p.scanComments, p.commentsScannedAt);
CREATE INDEX comment_id IF NOT EXISTS FOR (c:Comment) ON (c.id);
CREATE INDEX comment_scan IF NOT EXISTS FOR (c:Comment) ON (c.scan, c.scannedAt);
