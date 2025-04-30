package org.moera.search.scanner.ingest;

import java.util.Objects;
import jakarta.inject.Inject;

import org.moera.lib.node.types.PostingInfo;
import org.moera.search.data.Database;
import org.moera.search.data.PostingRepository;
import org.moera.search.data.PublicationRepository;
import org.moera.search.index.Index;
import org.moera.search.index.IndexedDocument;
import org.moera.search.index.LanguageAnalyzer;
import org.moera.search.media.MediaManager;
import org.moera.search.scanner.UpdateQueue;
import org.moera.search.scanner.updates.CommentsScanUpdate;
import org.moera.search.scanner.updates.PostingReactionsScanUpdate;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Component
public class PostingIngest {

    @Inject
    private Database database;

    @Inject
    private PostingRepository postingRepository;

    @Inject
    private PublicationRepository publicationRepository;

    @Inject
    private NodeIngest nodeIngest;

    @Inject
    private CommentIngest commentIngest;

    @Inject
    private ReactionIngest reactionIngest;

    @Inject
    private MediaManager mediaManager;

    @Inject
    private Index index;

    @Inject
    private LanguageAnalyzer languageAnalyzer;

    @Inject
    private UpdateQueue updateQueue;

    public void ingest(String nodeName, PostingInfo posting) {
        database.writeNoResult(() -> postingRepository.createPosting(nodeName, posting.getId()));
        if (!posting.getOwnerName().equals(nodeName)) {
            nodeIngest.newNode(posting.getOwnerName());
        }
        boolean hasReactions =
            posting.getReactions() != null
            && (
                !ObjectUtils.isEmpty(posting.getReactions().getPositive())
                || !ObjectUtils.isEmpty(posting.getReactions().getNegative())
            );
        database.writeNoResult(() -> {
            postingRepository.assignPostingOwner(nodeName, posting.getId(), posting.getOwnerName());
            if (posting.getTotalComments() == 0) {
                postingRepository.scanCommentsSucceeded(nodeName, posting.getId());
            }
            if (!hasReactions) {
                postingRepository.scanReactionsSucceeded(nodeName, posting.getId());
            }
            for (var feedReference : posting.getFeedReferences()) {
                if (feedReference.getFeedName().equals("timeline")) {
                    publicationRepository.addPublication(
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

        if (posting.getTotalComments() > 0) {
            updateQueue.offer(new CommentsScanUpdate(nodeName, posting.getId()));
        }
        if (hasReactions) {
            updateQueue.offer(new PostingReactionsScanUpdate(nodeName, posting.getId()));
        }
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
        var publishers = database.read(() -> publicationRepository.getPublishers(nodeName, posting.getId()));
        document.setPublishers(publishers);

        if (documentId == null) {
            var id = index.index(document);
            database.writeNoResult(() -> postingRepository.setDocumentId(nodeName, posting.getId(), id));
        } else {
            index.update(documentId, document);
        }
    }

    public void delete(String nodeName, String postingId) {
        commentIngest.deleteAll(nodeName, postingId);
        reactionIngest.deleteAll(nodeName, postingId);
        // delete the document first, so in the case of failure we will not lose documentId
        String documentId = database.read(() -> postingRepository.getDocumentId(nodeName, postingId));
        if (documentId != null) {
            index.delete(documentId);
        }
        database.writeNoResult(() -> {
            publicationRepository.deleteAllPublications(nodeName, postingId);
            postingRepository.deletePosting(nodeName, postingId);
        });
    }

    public void addPublication(
        String nodeName, String postingId, String publisherName, String feedName, String storyId, long publishedAt
    ) {
        database.writeNoResult(() ->
            publicationRepository.addPublication(nodeName, postingId, publisherName, feedName, storyId, publishedAt)
        );
        updatePublicationsInIndex(nodeName, postingId);
    }

    public void deletePublications(String nodeName, String postingId, String publisherName) {
        database.writeNoResult(() ->
            publicationRepository.deletePublications(nodeName, postingId, publisherName)
        );
        updatePublicationsInIndex(nodeName, postingId);
    }

    private void updatePublicationsInIndex(String nodeName, String postingId) {
        String documentId = database.read(() -> postingRepository.getDocumentId(nodeName, postingId));
        if (documentId != null && index.exists(documentId)) {
            var document = new IndexedDocument();
            var publishers = database.read(() -> publicationRepository.getPublishers(nodeName, postingId));
            document.setPublishers(publishers);
            index.update(documentId, document);
        }
    }

}
