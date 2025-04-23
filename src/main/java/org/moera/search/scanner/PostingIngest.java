package org.moera.search.scanner;

import java.io.IOException;
import java.util.Objects;
import jakarta.inject.Inject;

import org.moera.lib.node.types.PostingInfo;
import org.moera.search.data.Database;
import org.moera.search.data.PostingRepository;
import org.moera.search.index.Index;
import org.moera.search.index.IndexedDocument;
import org.moera.search.media.MediaManager;
import org.moera.search.util.ParametrizedLock;
import org.springframework.stereotype.Component;

@Component
public class PostingIngest {

    @Inject
    private Database database;

    @Inject
    private PostingRepository postingRepository;

    @Inject
    private NodeIngest nodeIngest;

    @Inject
    private MediaManager mediaManager;

    @Inject
    private Index index;

    private record PostingKey(String nodeName, String postingId) {
    }

    private final ParametrizedLock<PostingKey> postingLock = new ParametrizedLock<>();

    public boolean newPosting(String nodeName, String postingId) {
        try (var ignored = postingLock.lock(new PostingKey(nodeName, postingId))) {
            return database.write(() -> postingRepository.createPosting(nodeName, postingId));
        }
    }

    public void ingest(String nodeName, PostingInfo posting) throws IOException {
        if (!posting.getOwnerName().equals(nodeName)) {
            nodeIngest.newNode(posting.getOwnerName());
        }
        database.writeNoResult(() -> {
            postingRepository.assignPostingOwner(nodeName, posting.getId(), posting.getOwnerName());
            for (var feedReference : posting.getFeedReferences()) {
                if (feedReference.getFeedName().equals("timeline")) {
                    postingRepository.addPublication(
                        nodeName,
                        posting.getId(),
                        nodeName,
                        feedReference.getFeedName(),
                        feedReference.getStoryId(),
                        feedReference.getPublishedAt()
                    );
                }
            }
        });

        update(nodeName, posting);
    }

    public void update(String nodeName, PostingInfo posting) throws IOException {
        var revisionId = database.read(() -> postingRepository.getRevisionId(nodeName, posting.getId()));
        if (Objects.equals(revisionId, posting.getRevisionId())) {
            return;
        }

        database.writeNoResult(() -> postingRepository.fillPosting(nodeName, posting.getId(), posting));
        mediaManager.downloadAndSaveAvatar(
            nodeName,
            posting.getOwnerAvatar(),
            (avatarId, shape) -> {
                postingRepository.removeAvatar(nodeName, posting.getId());
                postingRepository.addAvatar(nodeName, posting.getId(), avatarId, shape);
            }
        );

        String documentId = database.read(() -> postingRepository.getDocumentId(nodeName, posting.getId()));
        var document = new IndexedDocument(nodeName, posting);
        var publishers = database.read(() -> postingRepository.getPublishers(nodeName, posting.getId()));
        document.setPublishers(publishers);

        if (documentId == null) {
            var id = index.index(document);
            database.writeNoResult(() -> postingRepository.setDocumentId(nodeName, posting.getId(), id));
        } else {
            index.update(documentId, document);
        }
    }

    public void addPublication(
        String nodeName, String postingId, String publisherName, String feedName, String storyId, long publishedAt
    ) throws IOException {
        database.writeNoResult(() ->
            postingRepository.addPublication(nodeName, postingId, publisherName, feedName, storyId, publishedAt)
        );
        String documentId = database.read(() -> postingRepository.getDocumentId(nodeName, postingId));
        if (documentId != null) {
            var document = new IndexedDocument();
            var publishers = database.read(() -> postingRepository.getPublishers(nodeName, postingId));
            document.setPublishers(publishers);
            index.update(documentId, document);
        }
    }

}
