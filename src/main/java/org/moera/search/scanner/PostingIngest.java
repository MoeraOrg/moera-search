package org.moera.search.scanner;

import java.util.Objects;
import jakarta.inject.Inject;

import org.moera.lib.node.types.PostingInfo;
import org.moera.search.data.Database;
import org.moera.search.data.PostingRepository;
import org.moera.search.index.Index;
import org.moera.search.index.IndexedDocument;
import org.moera.search.index.LanguageAnalyzer;
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

    @Inject
    private LanguageAnalyzer languageAnalyzer;

    private record PostingKey(String nodeName, String postingId) {
    }

    private final ParametrizedLock<PostingKey> postingLock = new ParametrizedLock<>();

    public boolean newPosting(String nodeName, String postingId) {
        try (var ignored = postingLock.lock(new PostingKey(nodeName, postingId))) {
            return database.write(() -> postingRepository.createPosting(nodeName, postingId));
        }
    }

    public void ingest(String nodeName, PostingInfo posting) {
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

    public void update(String nodeName, PostingInfo posting) {
        updateDatabase(nodeName, posting);
        updateIndex(nodeName, posting);
    }

    private void updateDatabase(String nodeName, PostingInfo posting) {
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
    }

    private void updateIndex(String nodeName, PostingInfo posting) {
        String documentId = database.read(() -> postingRepository.getDocumentId(nodeName, posting.getId()));
        String revisionId = documentId != null ? index.getRevisionId(documentId) : null;
        if (Objects.equals(revisionId, posting.getRevisionId())) {
            return;
        }

        var document = new IndexedDocument(nodeName, posting);
        languageAnalyzer.analyze(document);
        var publishers = database.read(() -> postingRepository.getPublishers(nodeName, posting.getId()));
        document.setPublishers(publishers);

        if (documentId == null) {
            var id = index.index(document);
            database.writeNoResult(() -> postingRepository.setDocumentId(nodeName, posting.getId(), id));
        } else {
            index.update(documentId, document);
        }
    }

    public void delete(String nodeName, String postingId) {
        // delete the document first, so in the case of failure we will not lose documentId
        String documentId = database.read(() -> postingRepository.getDocumentId(nodeName, postingId));
        if (documentId != null) {
            index.delete(documentId);
        }
        database.writeNoResult(() -> postingRepository.deletePosting(nodeName, postingId));
    }

    public void addPublication(
        String nodeName, String postingId, String publisherName, String feedName, String storyId, long publishedAt
    ) {
        database.writeNoResult(() ->
            postingRepository.addPublication(nodeName, postingId, publisherName, feedName, storyId, publishedAt)
        );
        updatePublicationsInIndex(nodeName, postingId);
    }

    public void deletePublications(String nodeName, String postingId, String publisherName) {
        database.writeNoResult(() ->
            postingRepository.deletePublications(nodeName, postingId, publisherName)
        );
        updatePublicationsInIndex(nodeName, postingId);
    }

    private void updatePublicationsInIndex(String nodeName, String postingId) {
        String documentId = database.read(() -> postingRepository.getDocumentId(nodeName, postingId));
        if (documentId != null) {
            var document = new IndexedDocument();
            var publishers = database.read(() -> postingRepository.getPublishers(nodeName, postingId));
            document.setPublishers(publishers);
            index.update(documentId, document);
        }
    }

}
