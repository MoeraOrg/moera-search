package org.moera.search.scanner;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.types.Scope;
import org.moera.lib.node.types.StoryType;
import org.moera.search.api.NodeApi;
import org.moera.search.data.NodeRepository;
import org.moera.search.data.PostingRepository;
import org.moera.search.index.Index;
import org.moera.search.index.IndexedDocument;
import org.moera.search.job.Job;
import org.moera.search.media.MediaManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

public class TimelineScanJob extends Job<TimelineScanJob.Parameters, TimelineScanJob.State> {

    public static class Parameters {

        private String nodeName;

        public Parameters() {
        }

        public Parameters(String nodeName) {
            this.nodeName = nodeName;
        }

        public String getNodeName() {
            return nodeName;
        }

        public void setNodeName(String nodeName) {
            this.nodeName = nodeName;
        }

    }

    public static class State {

        private long before = Long.MAX_VALUE;

        public State() {
        }

        public long getBefore() {
            return before;
        }

        public void setBefore(long before) {
            this.before = before;
        }

    }

    private static final Logger log = LoggerFactory.getLogger(TimelineScanJob.class);

    private static final int PAGE_SIZE = 50;

    @Inject
    private NodeApi nodeApi;

    @Inject
    private NodeRepository nodeRepository;

    @Inject
    private PostingRepository postingRepository;

    @Inject
    private MediaManager mediaManager;

    @Inject
    private Index index;

    public TimelineScanJob() {
        state = new State();
        retryCount(5, "PT10M");
    }

    @Override
    protected void setParameters(String parameters, ObjectMapper objectMapper) throws JsonProcessingException {
        this.parameters = objectMapper.readValue(parameters, Parameters.class);
    }

    @Override
    protected void setState(String state, ObjectMapper objectMapper) throws JsonProcessingException {
        this.state = objectMapper.readValue(state, State.class);
    }

    @Override
    protected void execute() throws Exception {
        while (state.before > 0) {
            var feedSlice = nodeApi
                .at(parameters.nodeName, generateCarte(parameters.nodeName, Scope.VIEW_ALL))
                .getFeedSlice("timeline", null, state.before, PAGE_SIZE);
            for (var story : feedSlice.getStories()) {
                state.before = story.getMoment();
                checkpoint();

                if (story.getStoryType() != StoryType.POSTING_ADDED) {
                    continue;
                }

                var posting = story.getPosting();
                boolean isOriginal = ObjectUtils.isEmpty(posting.getReceiverName());
                var sourceNodeName = isOriginal ? parameters.nodeName : posting.getReceiverName();
                var sourcePostingId = isOriginal ? posting.getId() : posting.getReceiverPostingId();
                if (!sourceNodeName.equals(parameters.nodeName)) {
                    database.executeWriteIgnoreConflict(() -> nodeRepository.createName(sourceNodeName));
                }
                if (!posting.getOwnerName().equals(parameters.nodeName)) {
                    database.executeWriteIgnoreConflict(() -> nodeRepository.createName(posting.getOwnerName()));
                }
                database.executeWriteWithoutResult(() -> {
                    postingRepository.createPosting(sourceNodeName, sourcePostingId);
                    postingRepository.assignPostingOwner(sourceNodeName, sourcePostingId, posting.getOwnerName());
                    postingRepository.addPublication(
                        sourceNodeName, sourcePostingId, parameters.nodeName, story.getId(), story.getPublishedAt()
                    );
                });

                if (isOriginal) {
                    database.executeWriteWithoutResult(() ->
                        postingRepository.fillPosting(sourceNodeName, sourcePostingId, posting)
                    );
                    mediaManager.downloadAndSaveAvatar(
                        parameters.nodeName,
                        posting.getOwnerAvatar(),
                        (avatarId, shape) -> {
                            postingRepository.removeAvatar(parameters.nodeName, posting.getId());
                            postingRepository.addAvatar(parameters.nodeName, posting.getId(), avatarId, shape);
                        }
                    );
                }

                String documentId = database.executeRead(() ->
                    postingRepository.getDocumentId(parameters.nodeName, posting.getId())
                );
                if (isOriginal || documentId != null) {
                    IndexedDocument document = isOriginal
                        ? new IndexedDocument(parameters.nodeName, posting)
                        : new IndexedDocument();
                    var publishers = database.executeRead(() ->
                        postingRepository.getPublishers(parameters.nodeName, posting.getId())
                    );
                    document.setPublishers(publishers);

                    if (documentId == null) {
                        var id = index.index(document);
                        database.executeWriteWithoutResult(() ->
                            postingRepository.setDocumentId(parameters.nodeName, posting.getId(), id)
                        );
                    } else {
                        index.update(documentId, document);
                    }
                }
            }
            state.before = feedSlice.getAfter();
            checkpoint();
        }

        database.executeWriteWithoutResult(() -> nodeRepository.scanTimelineSucceeded(parameters.nodeName));
    }

    @Override
    protected void failed() {
        super.failed();
        database.executeWriteWithoutResult(() -> nodeRepository.scanTimelineFailed(parameters.nodeName));
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " for " + parameters.nodeName;
    }

}
