package org.moera.search.scanner.updates;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.types.PostingInfo;
import org.moera.lib.node.types.Scope;
import org.moera.lib.node.types.StoryType;
import org.moera.search.api.NodeApi;
import org.moera.search.data.NodeRepository;
import org.moera.search.data.PostingRepository;
import org.moera.search.job.Job;
import org.moera.search.scanner.ingest.NodeIngest;
import org.moera.search.scanner.ingest.PostingIngest;
import org.moera.search.scanner.UpdateQueue;
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
                .at(parameters.nodeName, generateCarte(parameters.nodeName, Scope.VIEW_ALL))
                .getFeedSlice("timeline", null, state.before, PAGE_SIZE);
            for (var story : feedSlice.getStories()) {
                state.before = story.getMoment();
                checkpoint();

                if (story.getStoryType() == StoryType.POSTING_ADDED) {
                    PostingInfo posting = story.getPosting();
                    if (ObjectUtils.isEmpty(posting.getReceiverName())) {
                        boolean isAdded = database.read(() ->
                            postingRepository.exists(parameters.nodeName, posting.getId())
                        );
                        if (!isAdded) {
                            postingIngest.ingest(parameters.nodeName, posting);
                            database.writeNoResult(() ->
                                postingRepository.scanSucceeded(parameters.nodeName, posting.getId())
                            );
                        }
                    } else {
                        nodeIngest.newNode(posting.getReceiverName());
                        updateQueue.offer(
                            new PublicationAddUpdate(
                                posting.getReceiverName(),
                                posting.getReceiverPostingId(),
                                parameters.nodeName,
                                "timeline",
                                story.getId(),
                                story.getPublishedAt()
                            )
                        );
                    }
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
