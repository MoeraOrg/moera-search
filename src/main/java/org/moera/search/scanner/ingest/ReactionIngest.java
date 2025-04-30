package org.moera.search.scanner.ingest;

import jakarta.inject.Inject;

import org.moera.lib.node.types.ReactionInfo;
import org.moera.search.data.Database;
import org.moera.search.data.ReactionRepository;
import org.moera.search.media.MediaManager;
import org.springframework.stereotype.Component;

@Component
public class ReactionIngest {

    @Inject
    private Database database;

    @Inject
    private ReactionRepository reactionRepository;

    @Inject
    private NodeIngest nodeIngest;

    @Inject
    private MediaManager mediaManager;

    public void ingest(String nodeName, ReactionInfo reaction) {
        if (!reaction.getOwnerName().equals(nodeName)) {
            nodeIngest.newNode(reaction.getOwnerName());
        }
        database.writeNoResult(() -> reactionRepository.createReaction(nodeName, reaction));

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
    }

    public void delete(String nodeName, String postingId, String ownerName) {
        database.writeNoResult(() -> reactionRepository.deleteReaction(nodeName, postingId, ownerName));
    }

    public void delete(String nodeName, String postingId, String commentId, String ownerName) {
        database.writeNoResult(() -> reactionRepository.deleteReaction(nodeName, postingId, commentId, ownerName));
    }

    public void deleteAll(String nodeName, String postingId) {
        database.writeNoResult(() -> reactionRepository.deleteAllReactions(nodeName, postingId));
    }

    public void deleteAllInComments(String nodeName, String postingId) {
        database.writeNoResult(() -> reactionRepository.deleteAllReactionsInComments(nodeName, postingId));
    }

    public void deleteAll(String nodeName, String postingId, String commentId) {
        database.writeNoResult(() -> reactionRepository.deleteAllReactions(nodeName, postingId, commentId));
    }

}
