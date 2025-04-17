CREATE INDEX moera_node_scan_timeline FOR (n:MoeraNode) ON (n.scanTimeline, n.timelineScannedAt);
CREATE INDEX posting_id FOR (p:Posting) ON (p.id);
CREATE INDEX posting_scan FOR (p:Posting) ON (p.scan, p.scannedAt);
CREATE INDEX published_published_at FOR ()-[p:PUBLISHED]->() ON (p.publishedAt);
