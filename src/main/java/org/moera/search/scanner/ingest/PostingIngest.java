package org.moera.search.scanner.ingest;

import java.util.function.Supplier;
import jakarta.inject.Inject;

import org.moera.lib.node.types.PostingInfo;
import org.moera.search.Workload;
import org.moera.search.api.Feed;
import org.moera.search.data.Database;
import org.moera.search.data.EntryRepository;
import org.moera.search.data.PostingRepository;
import org.moera.search.data.PublicationRepository;
import org.moera.search.global.RequestCounter;
import org.moera.search.index.Index;
import org.moera.search.index.IndexedDocument;
import org.moera.search.index.LanguageAnalyzer;
import org.moera.search.media.MediaManager;
import org.moera.search.scanner.UpdateQueue;
import org.moera.search.scanner.updates.CommentsScanUpdate;
import org.moera.search.scanner.updates.PostingReactionsScanUpdate;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Component
public class PostingIngest {

    private static final Logger log = LoggerFactory.getLogger(PostingIngest.class);

    @Inject
    private RequestCounter requestCounter;

    @Inject
    private Database database;

    @Inject
    private EntryRepository entryRepository;

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
    private FavorIngest favorIngest;

    @Inject
    private HashtagIngest hashtagIngest;

    @Inject
    private AttachmentIngest attachmentIngest;

    @Inject
    private MediaManager mediaManager;

    @Inject
    private Index index;

    @Inject
    private LanguageAnalyzer languageAnalyzer;

    @Inject
    private UpdateQueue updateQueue;

    public void ingest(String nodeName, PostingInfo posting, Supplier<String> carteSupplier) {
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
                if (
                    feedReference.getFeedName().equals(Feed.TIMELINE)
                    || feedReference.getFeedName().equals(Feed.NEWS)
                ) {
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

        var documentId = update(nodeName, posting, carteSupplier);
        if (documentId != null) {
            database.writeNoResult(() -> entryRepository.allocateMoment(documentId, posting.getCreatedAt()));
        }

        if (posting.getTotalComments() > 0) {
            updateQueue.offer(new CommentsScanUpdate(nodeName, posting.getId()));
        }
        if (hasReactions) {
            updateQueue.offer(new PostingReactionsScanUpdate(nodeName, posting.getId()));
        }
    }

    public String update(String nodeName, PostingInfo posting, Supplier<String> carteSupplier) {
        return update(nodeName, posting, carteSupplier, false);
    }

    public String update(String nodeName, PostingInfo posting, Supplier<String> carteSupplier, boolean force) {
        updateDatabase(nodeName, posting, carteSupplier, force);
        return updateIndex(nodeName, posting, force);
    }

    private void updateDatabase(String nodeName, PostingInfo posting, Supplier<String> carteSupplier, boolean force) {
        if (!force) {
            var revision = database.read(() -> postingRepository.getRevision(nodeName, posting.getId()));
            if (revision.sameRevision(posting)) {
                return;
            }
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
        hashtagIngest.ingest(nodeName, posting);
        attachmentIngest.ingest(nodeName, posting);
        mediaManager.previewAndSavePrivateMedia(
            nodeName,
            carteSupplier,
            posting.getBody(),
            posting.getMedia(),
            () -> postingRepository.getMediaPreviewId(nodeName, posting.getId()),
            (mediaFileId, mediaId) -> {
                postingRepository.removeMediaPreview(nodeName, posting.getId());
                if (mediaFileId != null) {
                    postingRepository.addMediaPreview(nodeName, posting.getId(), mediaId, mediaFileId);
                }
            }
        );
    }

    private String updateIndex(String nodeName, PostingInfo posting, boolean force) {
        String documentId = database.read(() -> postingRepository.getDocumentId(nodeName, posting.getId()));
        if (!force) {
            var revision = documentId != null ? index.getRevision(documentId) : null;
            if (revision != null && revision.sameRevision(posting)) {
                return documentId;
            }
        }

        var document = new IndexedDocument(nodeName, posting);
        languageAnalyzer.analyze(document);
        database.readNoResult(() -> {
            document.setPublishers(publicationRepository.getPublishers(nodeName, posting.getId(), Feed.TIMELINE));
            document.setNews(publicationRepository.getPublishers(nodeName, posting.getId(), Feed.NEWS));
        });

        if (documentId == null) {
            var id = index.index(document);
            database.writeNoResult(() -> postingRepository.setDocumentId(nodeName, posting.getId(), id));
            documentId = id;
        } else {
            index.update(documentId, document);
        }

        return documentId;
    }

    public void delete(String nodeName, String postingId) {
        commentIngest.deleteAll(nodeName, postingId);
        reactionIngest.deleteAll(nodeName, postingId);
        deleteAllPublications(nodeName, postingId);
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
            publicationRepository.addPublication(nodeName, postingId, publisherName, feedName, storyId, publishedAt)
        );
        updatePublicationsInIndex(nodeName, postingId);
        favorIngest.publication(nodeName, postingId, publisherName, storyId, publishedAt);
    }

    public void deletePublications(String nodeName, String postingId, String publisherName) {
        favorIngest.deletePublication(nodeName, postingId, publisherName);
        database.writeNoResult(() ->
            publicationRepository.deletePublications(nodeName, postingId, publisherName)
        );
        try {
            updatePublicationsInIndex(nodeName, postingId);
        } catch (OpenSearchException e) {
            // ignore the exception because the document may have been deleted already
            if (!e.error().type().equals("document_missing_exception")) {
                throw e;
            }
        }
    }

    public void deleteAllPublications(String nodeName, String postingId) {
        favorIngest.deleteAllPublications(nodeName, postingId);
        database.writeNoResult(() -> publicationRepository.deleteAllPublications(nodeName, postingId));
    }

    private void updatePublicationsInIndex(String nodeName, String postingId) {
        String documentId = database.read(() -> postingRepository.getDocumentId(nodeName, postingId));
        if (documentId != null && index.exists(documentId)) {
            var document = new IndexedDocument();
            database.readNoResult(() -> {
                document.setPublishers(publicationRepository.getPublishers(nodeName, postingId, Feed.TIMELINE));
                document.setNews(publicationRepository.getPublishers(nodeName, postingId, Feed.NEWS));
            });
            index.update(documentId, document);
        }
    }

    @Scheduled(fixedDelayString = Workload.POSTING_POPULARITY_REFRESH_PERIOD)
    public void refreshPopularity() {
        if (!database.isReady()) {
            return;
        }

        try (var ignored = requestCounter.allot()) {
            try (var ignored2 = database.open()) {
                log.info("Refreshing popularity of postings");

                database.writeNoResult(() -> postingRepository.refreshReadPopularity());
                database.writeNoResult(() -> postingRepository.refreshCommentPopularity());
                database.writeNoResult(() -> postingRepository.refreshPopularity());

                log.info("Done refreshing popularity of postings");
            }
        }
    }

}
