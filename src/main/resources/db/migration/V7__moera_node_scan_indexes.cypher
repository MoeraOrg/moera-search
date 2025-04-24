CREATE INDEX moera_node_scan_profile IF NOT EXISTS FOR (n:MoeraNode) ON (n.scanProfile, n.profileScannedAt);
CREATE INDEX moera_node_subscribe IF NOT EXISTS FOR (n:MoeraNode) ON (n.subscribe, n.subscribedAt);
CREATE INDEX moera_node_scan_people IF NOT EXISTS FOR (n:MoeraNode) ON (n.scanPeople, n.peopleScannedAt);
CREATE INDEX moera_node_close_to_updated IF NOT EXISTS FOR (n:MoeraNode) ON (n.closeToUpdatedAt);
