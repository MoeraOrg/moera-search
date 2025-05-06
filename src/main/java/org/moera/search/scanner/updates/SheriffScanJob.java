package org.moera.search.scanner.updates;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.search.api.NodeApi;
import org.moera.search.data.NodeRepository;
import org.moera.search.job.Job;
import org.moera.search.scanner.ingest.SheriffMarkIngest;

public class SheriffScanJob extends Job<SheriffScanJob.Parameters, SheriffScanJob.State> {

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
        private boolean ordersScanned;

        public State() {
        }

        public long getBefore() {
            return before;
        }

        public void setBefore(long before) {
            this.before = before;
        }

        public boolean isOrdersScanned() {
            return ordersScanned;
        }

        public void setOrdersScanned(boolean ordersScanned) {
            this.ordersScanned = ordersScanned;
        }

    }

    private static final int PAGE_SIZE = 50;

    @Inject
    private NodeApi nodeApi;

    @Inject
    private NodeRepository nodeRepository;

    @Inject
    private SheriffMarkIngest sheriffMarkIngest;

    public SheriffScanJob() {
        state = new SheriffScanJob.State();
        retryCount(5, "PT10M");
    }

    @Override
    protected void setParameters(String parameters, ObjectMapper objectMapper) throws JsonProcessingException {
        this.parameters = objectMapper.readValue(parameters, SheriffScanJob.Parameters.class);
    }

    @Override
    protected void setState(String state, ObjectMapper objectMapper) throws JsonProcessingException {
        this.state = objectMapper.readValue(state, SheriffScanJob.State.class);
    }

    @Override
    protected void execute() throws Exception {
        if (!state.ordersScanned) {
            while (state.before > 0) {
                var ordersSlice = nodeApi
                    .at(parameters.nodeName)
                    .getRemoteSheriffOrdersSlice(null, state.before, PAGE_SIZE);
                for (var order : ordersSlice.getOrders()) {
                    state.before = order.getMoment();
                    checkpoint();

                    if (!Boolean.TRUE.equals(order.getDelete())) {
                        sheriffMarkIngest.ingest(
                            parameters.nodeName, order.getNodeName(), order.getPostingId(), order.getCommentId(), null
                        );
                    } else {
                        sheriffMarkIngest.delete(
                            parameters.nodeName, order.getNodeName(), order.getPostingId(), order.getCommentId(), null
                        );
                    }
                }
                state.before = ordersSlice.getAfter();
                checkpoint();
            }

            state.ordersScanned = true;
            state.before = Long.MAX_VALUE;
            checkpoint();
        }

        while (state.before > 0) {
            var userListSlice = nodeApi
                .at(parameters.nodeName)
                .getUserListSlice(SheriffMarkIngest.SHERIFF_USER_LIST_HIDE, null, state.before, PAGE_SIZE);
            for (var item : userListSlice.getItems()) {
                state.before = item.getMoment();
                checkpoint();

                sheriffMarkIngest.ingest(parameters.nodeName, null, null, null, item.getNodeName());
            }
            state.before = userListSlice.getAfter();
            checkpoint();
        }

        database.writeNoResult(() -> nodeRepository.scanSheriffSucceeded(parameters.nodeName));
    }

    @Override
    protected void failed() {
        super.failed();
        database.writeNoResult(() -> nodeRepository.scanSheriffFailed(parameters.nodeName));
    }

    @Override
    protected String getJobDescription() {
        return super.getJobDescription() + " for " + parameters.nodeName;
    }

}
