CREATE INDEX moera_node_scan_timeline IF NOT EXISTS FOR (n:MoeraNode) ON (n.scanTimeline, n.timelineScannedAt);
CREATE INDEX posting_id IF NOT EXISTS FOR (p:Posting) ON (p.id);
CREATE INDEX posting_scan IF NOT EXISTS FOR (p:Posting) ON (p.scan, p.scannedAt);
CREATE INDEX published_published_at IF NOT EXISTS FOR ()-[p:PUBLISHED]->() ON (p.publishedAt);
