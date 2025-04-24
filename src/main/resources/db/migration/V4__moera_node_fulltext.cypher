CREATE FULLTEXT INDEX moera_node_full_name IF NOT EXISTS FOR (n:MoeraNode) ON EACH [n.fullName]
OPTIONS {
    indexConfig: {
        `fulltext.analyzer`: 'english',
        `fulltext.eventually_consistent`: true
    }
};
