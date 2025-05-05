CREATE INDEX sheriff_posting_mark_primary IF NOT EXISTS
    FOR (sm:SheriffPostingMark) ON (sm.nodeName, sm.postingId);
CREATE INDEX sheriff_comment_mark_primary IF NOT EXISTS
    FOR (sm:SheriffCommentMark) ON (sm.nodeName, sm.postingId, sm.commentId);
