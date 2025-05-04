package org.moera.search.scanner.ingest;

import jakarta.inject.Inject;

import org.moera.lib.node.types.CommentInfo;
import org.moera.lib.node.types.PostingInfo;
import org.moera.search.data.Database;
import org.moera.search.data.HashtagRepository;
import org.moera.search.util.Util;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Component
public class HashtagIngest {

    @Inject
    private Database database;

    @Inject
    private HashtagRepository hashtagRepository;

    public void ingest(String nodeName, PostingInfo posting) {
        var hashtags = Util.extractHashtags(posting.getBody().getText());
        if (ObjectUtils.isEmpty(hashtags)) {
            return;
        }
        hashtags.forEach(hashtag -> database.writeIgnoreConflict(() -> hashtagRepository.createHashtag(hashtag)));
        database.writeNoResult(() -> {
            hashtagRepository.unmark(nodeName, posting.getId());
            hashtagRepository.mark(nodeName, posting.getId(), hashtags);
        });
    }

    public void ingest(String nodeName, CommentInfo comment) {
        var hashtags = Util.extractHashtags(comment.getBody().getText());
        if (ObjectUtils.isEmpty(hashtags)) {
            return;
        }
        hashtags.forEach(hashtag -> database.writeIgnoreConflict(() -> hashtagRepository.createHashtag(hashtag)));
        database.writeNoResult(() -> {
            hashtagRepository.unmark(nodeName, comment.getPostingId(), comment.getId());
            hashtagRepository.mark(nodeName, comment.getPostingId(), comment.getId(), hashtags);
        });
    }

}
