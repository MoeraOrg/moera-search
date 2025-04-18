package org.moera.search.scanner;

import java.io.IOException;
import jakarta.inject.Inject;

import org.moera.lib.node.types.PostingInfo;
import org.moera.search.data.Database;
import org.moera.search.data.NodeRepository;
import org.moera.search.data.PostingRepository;
import org.moera.search.index.Index;
import org.moera.search.index.IndexedDocument;
import org.moera.search.media.MediaManager;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Component
public class PostingIngest {

    @Inject
    private Database database;

    @Inject
    private NodeRepository nodeRepository;

    @Inject
    private PostingRepository postingRepository;

    @Inject
    private MediaManager mediaManager;

    @Inject
    private Index index;

    public void ingest(String nodeName, PostingInfo posting) throws IOException {
        boolean isOriginal = ObjectUtils.isEmpty(posting.getReceiverName());
        var sourceNodeName = isOriginal ? nodeName : posting.getReceiverName();
        var sourcePostingId = isOriginal ? posting.getId() : posting.getReceiverPostingId();
        if (!sourceNodeName.equals(nodeName)) {
            database.writeIgnoreConflict(() -> nodeRepository.createName(sourceNodeName));
        }
        if (!posting.getOwnerName().equals(nodeName)) {
            database.writeIgnoreConflict(() -> nodeRepository.createName(posting.getOwnerName()));
        }
        database.writeNoResult(() -> {
            postingRepository.createPosting(sourceNodeName, sourcePostingId);
            postingRepository.assignPostingOwner(sourceNodeName, sourcePostingId, posting.getOwnerName());
            for (var feedReference : posting.getFeedReferences()) {
                if (feedReference.getFeedName().equals("timeline")) {
                    postingRepository.addPublication(
                        sourceNodeName,
                        sourcePostingId,
                        nodeName,
                        feedReference.getStoryId(),
                        feedReference.getPublishedAt()
                    );
                }
            }
        });

        if (isOriginal) {
            database.writeNoResult(() ->
                postingRepository.fillPosting(sourceNodeName, sourcePostingId, posting)
            );
            mediaManager.downloadAndSaveAvatar(
                nodeName,
                posting.getOwnerAvatar(),
                (avatarId, shape) -> {
                    postingRepository.removeAvatar(nodeName, posting.getId());
                    postingRepository.addAvatar(nodeName, posting.getId(), avatarId, shape);
                }
            );
        }

        String documentId = database.read(() ->
            postingRepository.getDocumentId(nodeName, posting.getId())
        );
        if (isOriginal || documentId != null) {
            IndexedDocument document = isOriginal
                ? new IndexedDocument(nodeName, posting)
                : new IndexedDocument();
            var publishers = database.read(() ->
                postingRepository.getPublishers(nodeName, posting.getId())
            );
            document.setPublishers(publishers);

            if (documentId == null) {
                var id = index.index(document);
                database.writeNoResult(() ->
                    postingRepository.setDocumentId(nodeName, posting.getId(), id)
                );
            } else {
                index.update(documentId, document);
            }
        }
    }

}
