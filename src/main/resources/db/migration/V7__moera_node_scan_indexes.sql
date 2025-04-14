CREATE INDEX moera_node_scan_profile FOR (n:MoeraNode) ON (n.scanProfile, n.profileScannedAt);
CREATE INDEX moera_node_subscribe FOR (n:MoeraNode) ON (n.subscribe, n.subscribedAt);
CREATE INDEX moera_node_scan_people FOR (n:MoeraNode) ON (n.scanPeople, n.peopleScannedAt);
CREATE INDEX moera_node_close_to_updated FOR (n:MoeraNode) ON (n.closeToUpdatedAt);
