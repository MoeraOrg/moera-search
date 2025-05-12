package org.moera.search.scanner.updates;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.search.api.NodeApi;
import org.moera.search.data.NodeRepository;
import org.moera.search.job.Job;
import org.moera.search.scanner.UpdateQueue;
import org.moera.search.scanner.ingest.SheriffMarkIngest;
import org.moera.search.util.SafeInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        private long after = Long.MIN_VALUE;
        private boolean ordersScanned;

        public State() {
        }

        public long getAfter() {
            return after;
        }

        public void setAfter(long after) {
            this.after = after;
        }

        public boolean isOrdersScanned() {
            return ordersScanned;
        }

        public void setOrdersScanned(boolean ordersScanned) {
            this.ordersScanned = ordersScanned;
        }

    }

    private static final Logger log = LoggerFactory.getLogger(SheriffScanJob.class);

    private static final int PAGE_SIZE = 50;

    @Inject
    private NodeApi nodeApi;

    @Inject
    private NodeRepository nodeRepository;

    @Inject
    private UpdateQueue updateQueue;

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
        var scannedSheriff = database.read(() -> nodeRepository.isScanSheriffSucceeded(parameters.nodeName));
        if (scannedSheriff) {
            log.warn("Sheriff is scanned already, skipping");
            return;
        }

        if (!state.ordersScanned) {
            while (state.after < SafeInteger.MAX_VALUE) {
                var ordersSlice = nodeApi
                    .at(parameters.nodeName)
                    .getRemoteSheriffOrdersSlice(state.after, null, PAGE_SIZE);
                for (var order : ordersSlice.getOrders()) {
                    updateQueue.offer(new SheriffOrderUpdate(
                        Boolean.TRUE.equals(order.getDelete()), null, order.getNodeName(), order.getPostingId(),
                        order.getCommentId(), parameters.nodeName
                    ));

                    state.after = order.getMoment();
                    checkpoint();
                }
                state.after = ordersSlice.getBefore();
                checkpoint();

                if (ordersSlice.getTotalInFuture() == 0) {
                    break;
                }
            }

            state.ordersScanned = true;
            state.after = Long.MIN_VALUE;
            checkpoint();
        }

        while (state.after < SafeInteger.MAX_VALUE) {
            var userListSlice = nodeApi
                .at(parameters.nodeName)
                .getUserListSlice(SheriffMarkIngest.SHERIFF_USER_LIST_HIDE, state.after, null, PAGE_SIZE);
            for (var item : userListSlice.getItems()) {
                updateQueue.offer(new SheriffOrderUpdate(
                    false, item.getNodeName(), null, null, null, parameters.nodeName
                ));

                state.after = item.getMoment();
                checkpoint();
            }
            state.after = userListSlice.getBefore();
            checkpoint();

            if (userListSlice.getTotalInFuture() == 0) {
                break;
            }
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
