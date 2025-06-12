package org.moera.search.scanner.ingest;

import java.util.Objects;
import jakarta.inject.Inject;

import org.moera.lib.node.types.CommentInfo;
import org.moera.lib.node.types.MediaAttachment;
import org.moera.lib.node.types.PostingInfo;
import org.moera.search.data.AttachmentRepository;
import org.moera.search.data.Database;
import org.springframework.stereotype.Component;

@Component
public class AttachmentIngest {

    @Inject
    private Database database;

    @Inject
    private AttachmentRepository attachmentRepository;

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

}
