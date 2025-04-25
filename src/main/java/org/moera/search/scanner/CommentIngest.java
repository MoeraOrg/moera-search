package org.moera.search.scanner;

import java.util.Objects;
import jakarta.inject.Inject;

import org.moera.lib.node.types.CommentInfo;
import org.moera.search.data.CommentRepository;
import org.moera.search.data.Database;
import org.moera.search.index.Index;
import org.moera.search.index.IndexedDocument;
import org.moera.search.index.LanguageAnalyzer;
import org.moera.search.media.MediaManager;
import org.moera.search.util.ParametrizedLock;
import org.springframework.stereotype.Component;

@Component
public class CommentIngest {

    @Inject
    private Database database;

    @Inject
    private CommentRepository commentRepository;

    @Inject
    private NodeIngest nodeIngest;

    @Inject
    private MediaManager mediaManager;

    @Inject
    private Index index;

    @Inject
    private LanguageAnalyzer languageAnalyzer;

    private record CommentKey(String nodeName, String postingId, String commentId) {
    }

    private final ParametrizedLock<CommentKey> postingLock = new ParametrizedLock<>();

    public boolean newComment(String nodeName, String postingId, String commentId) {
        try (var ignored = postingLock.lock(new CommentKey(nodeName, postingId, commentId))) {
            return database.write(() -> commentRepository.createComment(nodeName, postingId, commentId));
        }
    }

    public void ingest(String nodeName, CommentInfo comment) {
        if (!comment.getOwnerName().equals(nodeName)) {
            nodeIngest.newNode(comment.getOwnerName());
        }
        if (comment.getRepliedTo() != null) {
            newComment(nodeName, comment.getPostingId(), comment.getRepliedTo().getId());
        }
        database.writeNoResult(() -> {
            commentRepository.assignCommentOwner(
                nodeName, comment.getPostingId(), comment.getId(), comment.getOwnerName()
            );
            if (comment.getRepliedTo() != null) {
                commentRepository.assignCommentRepliedTo(
                    nodeName, comment.getPostingId(), comment.getId(), comment.getRepliedTo().getId()
                );
            }
        });

        update(nodeName, comment);
    }

    public void update(String nodeName, CommentInfo comment) {
        updateDatabase(nodeName, comment);
        updateIndex(nodeName, comment);
    }

    private void updateDatabase(String nodeName, CommentInfo comment) {
        var revisionId = database.read(() ->
            commentRepository.getRevisionId(nodeName, comment.getPostingId(), comment.getId())
        );
        if (Objects.equals(revisionId, comment.getRevisionId())) {
            return;
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
    }

    private void updateIndex(String nodeName, CommentInfo comment) {
        String documentId = database.read(() ->
            commentRepository.getDocumentId(nodeName, comment.getPostingId(), comment.getId())
        );
        String revisionId = documentId != null ? index.getRevisionId(documentId) : null;
        if (Objects.equals(revisionId, comment.getRevisionId())) {
            return;
        }

        var document = new IndexedDocument(nodeName, comment);
        languageAnalyzer.analyze(document);

        if (documentId == null) {
            var id = index.index(document);
            database.writeNoResult(() ->
                commentRepository.setDocumentId(nodeName, comment.getPostingId(), comment.getId(), id)
            );
        } else {
            index.update(documentId, document);
        }
    }

}
