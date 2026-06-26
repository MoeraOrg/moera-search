package org.moera.search.scanner.ingest;

import jakarta.inject.Inject;

import org.moera.lib.node.types.CommentInfo;
import org.moera.lib.node.types.PostingInfo;
import org.moera.search.data.AttachmentRepository;
import org.moera.search.data.CommentRepository;
import org.moera.search.data.Database;
import org.moera.search.data.PostingRepository;
import org.moera.search.index.Index;
import org.moera.search.index.LanguageAnalyzer;
import org.springframework.stereotype.Component;

@Component
public class AttachmentIngest {

    @Inject
    private Database database;

    @Inject
    private AttachmentRepository attachmentRepository;

    @Inject
    private PostingRepository postingRepository;

    @Inject
    private CommentRepository commentRepository;

    @Inject
    private Index index;

    @Inject
    private LanguageAnalyzer languageAnalyzer;

    public void ingest(String nodeName, PostingInfo posting) {
        database.writeNoResult(() -> {
            attachmentRepository.deleteAll(nodeName, posting.getId());
            if (posting.getMedia() != null) {
                posting.getMedia().stream()
                    .filter(attachment -> attachment.getMedia() != null || attachment.getRemoteMedia() != null)
                    .forEach(attachment -> attachmentRepository.attach(nodeName, posting.getId(), attachment));
            }
        });
    }

    public void ingest(String nodeName, CommentInfo comment) {
        database.writeNoResult(() -> {
            attachmentRepository.deleteAll(nodeName, comment.getPostingId(), comment.getId());
            if (comment.getMedia() != null) {
                comment.getMedia().stream()
                    .filter(attachment -> attachment.getMedia() != null || attachment.getRemoteMedia() != null)
                    .forEach(attachment ->
                        attachmentRepository.attach(nodeName, comment.getPostingId(), comment.getId(), attachment)
                    );
            }
        });
    }

    public void updateMediaLocation(
        String nodeName, String postingId, String remoteMediaNodeName, String remoteMediaId, String mediaId
    ) {
        database.writeNoResult(() -> {
            attachmentRepository.updateMediaLocation(
                nodeName, postingId, remoteMediaNodeName, remoteMediaId, mediaId
            );
            postingRepository.updateMediaPreviewLocation(
                nodeName, postingId, remoteMediaNodeName, remoteMediaId, mediaId
            );
        });
    }

    public void updateMediaLocation(
        String nodeName, String postingId, String commentId, String remoteMediaNodeName, String remoteMediaId,
        String mediaId
    ) {
        database.writeNoResult(() -> {
            attachmentRepository.updateMediaLocation(
                nodeName, postingId, commentId, remoteMediaNodeName, remoteMediaId, mediaId
            );
            commentRepository.updateMediaPreviewLocation(
                nodeName, postingId, commentId, remoteMediaNodeName, remoteMediaId, mediaId
            );
        });
    }

    public void updateText(String nodeName, String postingId, String mediaId, String title, String textContent) {
        if (title == null && textContent == null) {
            return;
        }

        database.writeNoResult(() ->
            attachmentRepository.updateMediaText(nodeName, postingId, mediaId, title, textContent)
        );
        var documentId = database.read(() -> postingRepository.getDocumentId(nodeName, postingId));
        if (documentId == null) {
            return;
        }
        var mediaText = database.read(() -> attachmentRepository.getMediaText(nodeName, postingId));
        updateIndex(documentId, mediaText);
    }

    public void updateText(
        String nodeName, String postingId, String commentId, String mediaId, String title, String textContent
    ) {
        if (title == null && textContent == null) {
            return;
        }

        database.writeNoResult(() ->
            attachmentRepository.updateMediaText(nodeName, postingId, commentId, mediaId, title, textContent)
        );
        var documentId = database.read(() -> commentRepository.getDocumentId(nodeName, postingId, commentId));
        if (documentId == null) {
            return;
        }
        var mediaText = database.read(() -> attachmentRepository.getMediaText(nodeName, postingId, commentId));
        updateIndex(documentId, mediaText);
    }

    private void updateIndex(String documentId, String mediaText) {
        var document = index.get(documentId);
        if (document == null) {
            return;
        }
        document.setMediaText(mediaText);
        document.setMediaTextRu(null);
        languageAnalyzer.analyze(document);
        index.update(documentId, document);
    }

}
