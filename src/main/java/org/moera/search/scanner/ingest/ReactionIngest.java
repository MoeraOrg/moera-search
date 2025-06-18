package org.moera.search.scanner.ingest;

import jakarta.inject.Inject;

import org.moera.lib.node.types.ReactionInfo;
import org.moera.search.data.Database;
import org.moera.search.data.PostingRepository;
import org.moera.search.data.ReactionRepository;
import org.moera.search.media.MediaManager;
import org.springframework.stereotype.Component;

@Component
public class ReactionIngest {

    @Inject
    private Database database;

    @Inject
    private PostingRepository postingRepository;

    @Inject
    private ReactionRepository reactionRepository;

    @Inject
    private NodeIngest nodeIngest;

    @Inject
    private FavorIngest favorIngest;

    @Inject
    private MediaManager mediaManager;

    public void ingest(String nodeName, ReactionInfo reaction) {
        if (!reaction.getOwnerName().equals(nodeName)) {
            nodeIngest.newNode(reaction.getOwnerName());
        }
        database.writeNoResult(() -> {
            reactionRepository.createReaction(nodeName, reaction);
            if (reaction.getCommentId() == null) {
                postingRepository.updateRecommendationOrder(nodeName, reaction.getPostingId());
            }
        });

        MediaManager.AvatarSaver avatarSaver;
        if (reaction.getCommentId() == null) {
            avatarSaver = (avatarId, shape) -> {
                reactionRepository.removeAvatar(nodeName, reaction.getPostingId(), reaction.getOwnerName());
                reactionRepository.addAvatar(
                    nodeName, reaction.getPostingId(), reaction.getOwnerName(), avatarId, shape
                );
            };
        } else {
            avatarSaver = (avatarId, shape) -> {
                reactionRepository.removeAvatar(
                    nodeName, reaction.getPostingId(), reaction.getCommentId(), reaction.getOwnerName()
                );
                reactionRepository.addAvatar(
                    nodeName, reaction.getPostingId(), reaction.getCommentId(), reaction.getOwnerName(), avatarId, shape
                );
            };
        }
        mediaManager.downloadAndSaveAvatar(nodeName, reaction.getOwnerAvatar(), avatarSaver);
        favorIngest.reaction(nodeName, reaction);
    }

    public void delete(String nodeName, String postingId, String ownerName) {
        favorIngest.deleteReaction(nodeName, postingId, ownerName);
        database.writeNoResult(() -> {
            reactionRepository.deleteReaction(nodeName, postingId, ownerName);
            postingRepository.updateRecommendationOrder(nodeName, postingId);
        });
    }

    public void delete(String nodeName, String postingId, String commentId, String ownerName) {
        favorIngest.deleteReaction(nodeName, postingId, commentId, ownerName);
        database.writeNoResult(() -> reactionRepository.deleteReaction(nodeName, postingId, commentId, ownerName));
    }

    public void deleteAll(String nodeName, String postingId) {
        favorIngest.deleteAllReactions(nodeName, postingId);
        database.writeNoResult(() -> {
            reactionRepository.deleteAllReactions(nodeName, postingId);
            postingRepository.updateRecommendationOrder(nodeName, postingId);
        });
    }

    public void deleteAllInComments(String nodeName, String postingId) {
        favorIngest.deleteAllReactionsInComments(nodeName, postingId);
        database.writeNoResult(() -> reactionRepository.deleteAllReactionsInComments(nodeName, postingId));
    }

    public void deleteAll(String nodeName, String postingId, String commentId) {
        favorIngest.deleteAllReactions(nodeName, postingId, commentId);
        database.writeNoResult(() -> reactionRepository.deleteAllReactions(nodeName, postingId, commentId));
    }

}
