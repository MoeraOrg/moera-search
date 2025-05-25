package org.moera.search.scanner.updates;

import java.util.Objects;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.types.PostingInfo;
import org.moera.lib.node.types.Scope;
import org.moera.lib.node.types.StoryType;
import org.moera.search.api.Feed;
import org.moera.search.api.MoeraNodeUncheckedException;
import org.moera.search.api.NodeApi;
import org.moera.search.data.NodeRepository;
import org.moera.search.data.PostingRepository;
import org.moera.search.job.Job;
import org.moera.search.scanner.UpdateQueue;
import org.moera.search.scanner.ingest.NodeIngest;
import org.moera.search.scanner.ingest.PostingIngest;
import org.moera.search.scanner.signature.PostingSignatureVerifier;
import org.moera.search.scanner.signature.SignatureVerificationException;
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
        private String validatedId;

        public State() {
        }

        public long getBefore() {
            return before;
        }

        public void setBefore(long before) {
            this.before = before;
        }

        public String getValidatedId() {
            return validatedId;
        }

        public void setValidatedId(String validatedId) {
            this.validatedId = validatedId;
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
    private NodeIngest nodeIngest;

    @Inject
    private PostingIngest postingIngest;

    @Inject
    private PostingSignatureVerifier postingSignatureVerifier;

    @Inject
    private UpdateQueue updateQueue;

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
                .at(parameters.nodeName, generateCarte(parameters.nodeName, Scope.VIEW_CONTENT))
                .getFeedSlice(Feed.TIMELINE, null, state.before, PAGE_SIZE);
            for (var story : feedSlice.getStories()) {
                state.before = story.getMoment();
                state.validatedId = null;
                checkpoint();

                if (story.getStoryType() != StoryType.POSTING_ADDED) {
                    continue;
                }

                PostingInfo posting = story.getPosting();
                if (ObjectUtils.isEmpty(posting.getReceiverName())) {
                    // On the next retry the posting may exist (partially), so need to skip the check
                    if (!Objects.equals(state.validatedId, posting.getId())) {
                        boolean isAdded = database.read(() ->
                            postingRepository.exists(parameters.nodeName, posting.getId())
                        );
                        if (isAdded) {
                            continue;
                        }
                        state.validatedId = posting.getId();
                        checkpoint();
                    }
                    if (posting.getSignature() == null) {
                        log.info("Posting is not signed, skipping");
                        continue;
                    }
                    try {
                        postingSignatureVerifier.verifySignature(
                            parameters.nodeName,
                            posting,
                            generateCarte(parameters.nodeName, Scope.VIEW_CONTENT)
                        );
                        postingIngest.ingest(
                            parameters.nodeName, posting, carteSupplier(parameters.nodeName, Scope.VIEW_CONTENT)
                        );
                        database.writeNoResult(() ->
                            postingRepository.scanSucceeded(parameters.nodeName, posting.getId())
                        );
                    } catch (SignatureVerificationException e) {
                        log.error("Incorrect signature of posting {}: {}", posting.getId(), e.getMessage());
                    } catch (MoeraNodeUncheckedException e) {
                        if (isRecoverableError(e)) {
                            throw e;
                        }
                        log.error("Cannot verify signature of posting {}: {}", posting.getId(), e.getMessage());
                        log.debug("Cannot verify signature", e);
                    }
                } else {
                    nodeIngest.newNode(posting.getReceiverName());
                    updateQueue.offer(
                        new PublicationAddUpdate(
                            posting.getReceiverName(),
                            posting.getReceiverPostingId(),
                            parameters.nodeName,
                            Feed.TIMELINE,
                            story.getId(),
                            story.getPublishedAt()
                        )
                    );
                }
            }
            state.before = feedSlice.getAfter();
            checkpoint();
        }

        database.writeNoResult(() -> nodeRepository.scanTimelineSucceeded(parameters.nodeName));
    }

    @Override
    protected void failed() {
        super.failed();
        database.writeNoResult(() -> nodeRepository.scanTimelineFailed(parameters.nodeName));
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " for " + parameters.nodeName;
    }

}
