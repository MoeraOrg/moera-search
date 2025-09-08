MATCH ()-[r:WAS_RECOMMENDED|DONT_RECOMMEND]->(:Posting) SET r.feedName = "news";
