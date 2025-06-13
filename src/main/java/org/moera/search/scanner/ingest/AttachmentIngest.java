package org.moera.search.scanner.ingest;

import java.util.Objects;
import jakarta.inject.Inject;

import org.moera.lib.node.types.CommentInfo;
import org.moera.lib.node.types.MediaAttachment;
import org.moera.lib.node.types.PostingInfo;
import org.moera.search.data.AttachmentRepository;
import org.moera.search.data.CommentRepository;
import org.moera.search.data.Database;
import org.moera.search.data.PostingRepository;
import org.moera.search.index.Index;
import org.moera.search.index.IndexedDocument;
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
                    .map(MediaAttachment::getMedia)
                    .filter(Objects::nonNull)
                    .forEach(media -> attachmentRepository.attach(nodeName, posting.getId(), media));
            }
        });
    }

    public void ingest(String nodeName, CommentInfo comment) {
        database.writeNoResult(() -> {
            attachmentRepository.deleteAll(nodeName, comment.getPostingId(), comment.getId());
            if (comment.getMedia() != null) {
                comment.getMedia().stream()
                    .map(MediaAttachment::getMedia)
                    .filter(Objects::nonNull)
                    .forEach(media ->
                        attachmentRepository.attach(nodeName, comment.getPostingId(), comment.getId(), media)
                    );
            }
        });
    }

    public void updateText(String nodeName, String postingId, String mediaId, String textContent) {
        database.writeNoResult(() -> attachmentRepository.setTextContent(nodeName, postingId, mediaId, textContent));
        var documentId = database.read(() -> postingRepository.getDocumentId(nodeName, postingId));
        if (documentId == null) {
            return;
        }
        var mediaText = database.read(() -> attachmentRepository.getMediaText(nodeName, postingId));
        updateIndex(documentId, mediaText);
    }

    public void updateText(String nodeName, String postingId, String commentId, String mediaId, String textContent) {
        database.writeNoResult(() ->
            attachmentRepository.setTextContent(nodeName, postingId, commentId, mediaId, textContent)
        );
        var documentId = database.read(() -> commentRepository.getDocumentId(nodeName, postingId, commentId));
        if (documentId == null) {
            return;
        }
        var mediaText = database.read(() -> attachmentRepository.getMediaText(nodeName, postingId, commentId));
        updateIndex(documentId, mediaText);
    }

    private void updateIndex(String documentId, String mediaText) {
        var document = new IndexedDocument();
        document.setMediaText(mediaText);
        languageAnalyzer.analyze(document);
        index.update(documentId, document);
    }

}
