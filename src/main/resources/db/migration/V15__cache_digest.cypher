CREATE INDEX cache_media_digest_primary IF NOT EXISTS FOR (ch:CacheMediaDigest) ON (ch.nodeName, ch.mediaId);
CREATE INDEX cache_posting_digest_primary IF NOT EXISTS
    FOR (ch:CachePostingDigest) ON (ch.nodeName, ch.postingId, ch.revisionId);
CREATE INDEX cache_comment_digest_primary IF NOT EXISTS
    FOR (ch:CacheCommentDigest) ON (ch.nodeName, ch.postingId, ch.commentId, ch.revisionId);
