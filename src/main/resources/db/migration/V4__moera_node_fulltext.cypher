CREATE FULLTEXT INDEX moera_node_full_name FOR (n:MoeraNode) ON EACH [n.fullName]
OPTIONS {
    indexConfig: {
        `fulltext.analyzer`: 'english',
        `fulltext.eventually_consistent`: true
    }
};
