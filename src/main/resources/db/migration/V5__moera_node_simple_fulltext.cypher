DROP INDEX moera_node_full_name IF EXISTS;
CREATE FULLTEXT INDEX moera_node_full_name FOR (n:MoeraNode) ON EACH [n.fullName]
OPTIONS {
    indexConfig: {
        `fulltext.analyzer`: 'simple',
        `fulltext.eventually_consistent`: true
    }
};
