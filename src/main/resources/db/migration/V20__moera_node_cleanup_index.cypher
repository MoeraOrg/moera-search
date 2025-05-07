CREATE INDEX moera_node_close_to_cleaned_up IF NOT EXISTS FOR (n:MoeraNode) ON (n.closeToCleanedUpAt);
