package org.moera.search.scanner.ingest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import jakarta.inject.Inject;

import org.moera.lib.node.types.CommentInfo;
import org.moera.lib.node.types.ReactionInfo;
import org.moera.search.Workload;
import org.moera.search.data.Database;
import org.moera.search.data.FavorRepository;
import org.moera.search.data.FavorType;
import org.moera.search.data.PostingRepository;
import org.moera.search.global.RequestCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FavorIngest {

    private static final Logger log = LoggerFactory.getLogger(FavorIngest.class);

    @Inject
    private RequestCounter requestCounter;

    @Inject
    private Database database;

    @Inject
    private FavorRepository favorRepository;

    @Inject
    private PostingRepository postingRepository;

    public void publication(
        String nodeName, String postingId, String publisherName, String storyId, long publishedAt
    ) {
        Instant createdAt = Instant.ofEpochSecond(publishedAt);
        Instant deadline = createdAt.plus(FavorType.PUBLICATION.getDecayHours(), ChronoUnit.HOURS);
        if (deadline.isBefore(Instant.now())) {
            return;
        }

        database.writeNoResult(() ->
            favorRepository.createPublicationFavors(
                nodeName, postingId, publisherName, storyId, createdAt.toEpochMilli(), deadline.toEpochMilli()
            )
        );
    }

    public void deletePublication(String nodeName, String postingId, String publisherName) {
        database.writeNoResult(() -> favorRepository.deletePublicationFavors(nodeName, postingId, publisherName));
    }

    public void deleteAllPublications(String nodeName, String postingId) {
        database.writeNoResult(() -> favorRepository.deleteAllPublicationFavors(nodeName, postingId));
    }

    public void comment(String nodeName, CommentInfo comment) {
        Instant createdAt = Instant.ofEpochSecond(comment.getCreatedAt());
        Instant deadline = createdAt.plus(FavorType.COMMENT.getDecayHours(), ChronoUnit.HOURS);
        if (!deadline.isBefore(Instant.now())) {
            database.writeNoResult(() ->
                favorRepository.createCommentFavors(
                    nodeName, comment.getPostingId(), comment.getId(), createdAt.toEpochMilli(), deadline.toEpochMilli()
                )
            );
        }

        if (comment.getRepliedTo() == null || comment.getRepliedTo().getName().equals(comment.getOwnerName())) {
            return;
        }
        Instant replyDeadline = createdAt.plus(FavorType.REPLY_TO_COMMENT.getDecayHours(), ChronoUnit.HOURS);
        if (!replyDeadline.isBefore(Instant.now())) {
            database.writeNoResult(() ->
                favorRepository.createRepliedToFavor(
                    nodeName, comment.getPostingId(), comment.getId(), createdAt.toEpochMilli(),
                    replyDeadline.toEpochMilli()
                )
            );
        }
    }

    public void deleteComment(String nodeName, String postingId, String commentId) {
        database.writeNoResult(() -> favorRepository.deleteCommentFavors(nodeName, postingId, commentId));
    }

    public void deleteAllComments(String nodeName, String postingId) {
        database.writeNoResult(() -> favorRepository.deleteAllCommentFavors(nodeName, postingId));
    }

    public void reaction(String nodeName, ReactionInfo reaction) {
        if (Boolean.TRUE.equals(reaction.getNegative())) {
            return;
        }

        Instant createdAt = Instant.ofEpochSecond(reaction.getCreatedAt());
        if (reaction.getCommentId() == null) {
            Instant deadline = createdAt.plus(FavorType.LIKE_POST.getDecayHours(), ChronoUnit.HOURS);
            if (deadline.isBefore(Instant.now())) {
                return;
            }

            database.writeNoResult(() ->
                favorRepository.createPostingReactionFavors(
                    nodeName, reaction.getPostingId(), reaction.getOwnerName(), createdAt.toEpochMilli(),
                    deadline.toEpochMilli()
                )
            );
        } else {
            Instant deadline = createdAt.plus(FavorType.LIKE_COMMENT.getDecayHours(), ChronoUnit.HOURS);
            if (deadline.isBefore(Instant.now())) {
                return;
            }

            database.writeNoResult(() ->
                favorRepository.createCommentReactionFavors(
                    nodeName, reaction.getPostingId(), reaction.getCommentId(), reaction.getOwnerName(),
                    createdAt.toEpochMilli(), deadline.toEpochMilli()
                )
            );
        }
    }

    public void deleteReaction(String nodeName, String postingId, String ownerName) {
        database.writeNoResult(() -> favorRepository.deletePostingReaction(nodeName, postingId, ownerName));
    }

    public void deleteReaction(String nodeName, String postingId, String commentId, String ownerName) {
        database.writeNoResult(() -> favorRepository.deleteCommentReaction(nodeName, postingId, commentId, ownerName));
    }

    public void deleteAllReactions(String nodeName, String postingId) {
        database.writeNoResult(() -> favorRepository.deleteAllPostingReactions(nodeName, postingId));
    }

    public void deleteAllReactions(String nodeName, String postingId, String commentId) {
        database.writeNoResult(() -> favorRepository.deleteAllCommentReactions(nodeName, postingId, commentId));
    }

    public void deleteAllReactionsInComments(String nodeName, String postingId) {
        database.writeNoResult(() -> favorRepository.deleteAllReactionsInComments(nodeName, postingId));
    }

    public void novice(String nodeName, String postingId) {
        Instant createdAt = Instant.now();
        Instant deadline = createdAt.plus(FavorType.NOVICE.getDecayHours(), ChronoUnit.HOURS);
        if (deadline.isBefore(Instant.now())) {
            return;
        }

        database.writeNoResult(() ->
            favorRepository.createNoviceFavors(nodeName, postingId, createdAt.toEpochMilli(), deadline.toEpochMilli())
        );
    }

    public void deleteNovice(String nodeName, String postingId) {
        database.writeNoResult(() -> favorRepository.deleteNoviceFavors(nodeName, postingId));
    }

    @Scheduled(fixedDelayString = Workload.FAVORS_PURGE_PERIOD)
    public void purgeExpired() {
        if (!database.isReady()) {
            return;
        }

        try (var ignored = requestCounter.allot()) {
            try (var ignored2 = database.open()) {
                log.info("Purging expired favors");

                database.writeNoResult(() -> {
                    postingRepository.updateUnfavoredRecommendationOrder();
                    favorRepository.purgeExpired();
                });
            }
        }
    }

}
