MATCH (n:MoeraNode) SET n.subscribe = null, n.subscribedAt = null;
DROP INDEX moera_node_subscribe IF EXISTS;
