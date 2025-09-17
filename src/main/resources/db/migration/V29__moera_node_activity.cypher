CREATE INDEX moera_node_activity IF NOT EXISTS FOR (n:MoeraNode) ON (n.activity);
CREATE INDEX moera_node_activity_updated IF NOT EXISTS FOR (n:MoeraNode) ON (n.activityUpdatedAt);
