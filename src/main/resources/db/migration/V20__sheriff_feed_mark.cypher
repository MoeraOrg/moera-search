CREATE INDEX sheriff_feed_mark_primary IF NOT EXISTS FOR (sm:SheriffFeedMark) ON (sm.nodeName, sm.feedName);
