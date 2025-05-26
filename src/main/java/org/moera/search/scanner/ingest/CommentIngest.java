package org.moera.search.scanner.ingest;

import java.util.function.Supplier;
import jakarta.inject.Inject;

import org.moera.lib.node.types.CommentInfo;
import org.moera.search.data.CommentRepository;
import org.moera.search.data.Database;
import org.moera.search.data.EntryRepository;
import org.moera.search.index.Index;
import org.moera.search.index.IndexedDocument;
import org.moera.search.index.LanguageAnalyzer;
import org.moera.search.media.MediaManager;
import org.moera.search.scanner.UpdateQueue;
import org.moera.search.scanner.updates.CommentAddUpdate;
import org.moera.search.scanner.updates.CommentReactionsScanUpdate;
import org.moera.search.scanner.updates.CommentRepliedToUpdate;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Component
public class CommentIngest {

    @Inject
    private Database database;

    @Inject
    private EntryRepository entryRepository;

    @Inject
    private CommentRepository commentRepository;

    @Inject
    private NodeIngest nodeIngest;

    @Inject
    private ReactionIngest reactionIngest;

    @Inject
    private FavorIngest favorIngest;

    @Inject
    private HashtagIngest hashtagIngest;

    @Inject
    private MediaManager mediaManager;

    @Inject
    private Index index;

    @Inject
    private LanguageAnalyzer languageAnalyzer;

    @Inject
    private UpdateQueue updateQueue;

    public void ingest(String nodeName, CommentInfo comment, Supplier<String> carteSupplier) {
        database.writeNoResult(() ->
            commentRepository.createComment(nodeName, comment.getPostingId(), comment.getId())
        );
        if (!comment.getOwnerName().equals(nodeName)) {
            nodeIngest.newNode(comment.getOwnerName());
        }
        var waitRepliedTo = comment.getRepliedTo() != null
            ? database.read(() ->
                !commentRepository.exists(nodeName, comment.getPostingId(), comment.getRepliedTo().getId())
            )
            : false;
        boolean hasReactions =
            comment.getReactions() != null
            && (
                !ObjectUtils.isEmpty(comment.getReactions().getPositive())
                || !ObjectUtils.isEmpty(comment.getReactions().getNegative())
            );
        database.writeNoResult(() -> {
            commentRepository.assignCommentOwner(
                nodeName, comment.getPostingId(), comment.getId(), comment.getOwnerName()
            );
            if (comment.getRepliedTo() != null && !waitRepliedTo) {
                commentRepository.assignCommentRepliedTo(
                    nodeName, comment.getPostingId(), comment.getId(), comment.getRepliedTo().getId()
                );
            }
            if (!hasReactions) {
                commentRepository.scanReactionsSucceeded(nodeName, comment.getPostingId(), comment.getId());
            }
        });

        var documentId = update(nodeName, comment, carteSupplier);
        if (documentId != null) {
            database.writeNoResult(() -> entryRepository.allocateMoment(documentId, comment.getCreatedAt()));
        }

        favorIngest.comment(nodeName, comment);

        if (waitRepliedTo) {
            updateQueue.offer(new CommentAddUpdate(nodeName, comment.getPostingId(), comment.getRepliedTo().getId()));
            updateQueue.offer(
                new CommentRepliedToUpdate(
                    nodeName, comment.getPostingId(), comment.getId(), comment.getRepliedTo().getId()
                )
            );
        }
        if (hasReactions) {
            updateQueue.offer(new CommentReactionsScanUpdate(nodeName, comment.getPostingId(), comment.getId()));
        }
    }

    public String update(String nodeName, CommentInfo comment, Supplier<String> carteSupplier) {
        return update(nodeName, comment, carteSupplier, false);
    }

    public String update(String nodeName, CommentInfo comment, Supplier<String> carteSupplier, boolean force) {
        updateDatabase(nodeName, comment, carteSupplier, force);
        return updateIndex(nodeName, comment);
    }

    private void updateDatabase(String nodeName, CommentInfo comment, Supplier<String> carteSupplier, boolean force) {
        if (!force) {
            var revision = database.read(() ->
                commentRepository.getRevision(nodeName, comment.getPostingId(), comment.getId())
            );
            if (revision.sameRevision(comment)) {
                return;
            }
        }

        database.writeNoResult(() ->
            commentRepository.fillComment(nodeName, comment.getPostingId(), comment.getId(), comment)
        );
        mediaManager.downloadAndSaveAvatar(
            nodeName,
            comment.getOwnerAvatar(),
            (avatarId, shape) -> {
                commentRepository.removeAvatar(nodeName, comment.getPostingId(), comment.getId());
                commentRepository.addAvatar(nodeName, comment.getPostingId(), comment.getId(), avatarId, shape);
            }
        );
        hashtagIngest.ingest(nodeName, comment);
        mediaManager.previewAndSavePrivateMedia(
            nodeName,
            carteSupplier,
            comment.getBody(),
            comment.getMedia(),
            () -> commentRepository.getMediaPreviewId(nodeName, comment.getPostingId(), comment.getId()),
            (mediaFileId, mediaId) -> {
                commentRepository.removeMediaPreview(nodeName, comment.getPostingId(), comment.getId());
                if (mediaFileId != null) {
                    commentRepository.addMediaPreview(
                        nodeName, comment.getPostingId(), comment.getId(), mediaId, mediaFileId
                    );
                }
            }
        );
    }

    private String updateIndex(String nodeName, CommentInfo comment) {
        String documentId = database.read(() ->
            commentRepository.getDocumentId(nodeName, comment.getPostingId(), comment.getId())
        );
        var revision = documentId != null ? index.getRevision(documentId) : null;
        if (revision != null && revision.sameRevision(comment)) {
            return documentId;
        }

        var document = new IndexedDocument(nodeName, comment);
        languageAnalyzer.analyze(document);

        if (documentId == null) {
            var id = index.index(document);
            database.writeNoResult(() ->
                commentRepository.setDocumentId(nodeName, comment.getPostingId(), comment.getId(), id)
            );
            documentId = id;
        } else {
            index.update(documentId, document);
        }

        return documentId;
    }

    public void delete(String nodeName, String postingId, String commentId) {
        reactionIngest.deleteAll(nodeName, postingId, commentId);
        favorIngest.deleteComment(nodeName, postingId, commentId);
        // delete the document first, so in the case of failure we will not lose documentId
        String documentId = database.read(() -> commentRepository.getDocumentId(nodeName, postingId, commentId));
        if (documentId != null) {
            index.delete(documentId);
        }
        database.writeNoResult(() -> commentRepository.deleteComment(nodeName, postingId, commentId));
    }

    public void deleteAll(String nodeName, String postingId) {
        reactionIngest.deleteAllInComments(nodeName, postingId);
        favorIngest.deleteAllComments(nodeName, postingId);
        var documentIds = database.read(() -> commentRepository.getAllDocumentIds(nodeName, postingId));
        if (!documentIds.isEmpty()) {
            index.deleteBulk(documentIds);
        }
        database.writeNoResult(() -> commentRepository.deleteAllComments(nodeName, postingId));
    }

}
