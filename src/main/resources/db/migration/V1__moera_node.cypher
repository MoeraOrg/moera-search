CREATE CONSTRAINT moera_node_name IF NOT EXISTS FOR (n:MoeraNode) REQUIRE n.name IS UNIQUE;
