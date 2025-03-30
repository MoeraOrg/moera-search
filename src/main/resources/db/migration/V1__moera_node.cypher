CREATE CONSTRAINT moera_node_name FOR (n:MoeraNode) REQUIRE n.name IS UNIQUE;
