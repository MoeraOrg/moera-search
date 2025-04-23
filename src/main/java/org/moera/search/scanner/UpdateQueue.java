package org.moera.search.scanner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.types.SearchBlockUpdate;
import org.moera.lib.node.types.SearchContentUpdateType;
import org.moera.lib.node.types.SearchFriendUpdate;
import org.moera.lib.node.types.SearchPostingUpdate;
import org.moera.lib.node.types.SearchSubscriptionUpdate;
import org.moera.lib.util.LogUtil;
import org.moera.search.Workload;
import org.moera.search.data.Database;
import org.moera.search.data.DatabaseInitializedEvent;
import org.moera.search.data.NodeRepository;
import org.moera.search.data.PendingUpdate;
import org.moera.search.data.PendingUpdateRepository;
import org.moera.search.global.RequestCounter;
import org.moera.search.job.Jobs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class UpdateQueue {

    private static final Logger log = LoggerFactory.getLogger(UpdateQueue.class);

    private List<PendingUpdate> queue = new ArrayList<>();
    private final Object lock = new Object();

    @Inject
    private RequestCounter requestCounter;

    @Inject
    private Database database;

    @Inject
    private PendingUpdateRepository pendingUpdateRepository;

    @Inject
    private NodeRepository nodeRepository;

    @Inject
    private PostingIngest postingIngest;

    @Inject
    private Jobs jobs;

    @Inject
    private ObjectMapper objectMapper;

    @EventListener(DatabaseInitializedEvent.class)
    public void init() {
        try (var ignored = requestCounter.allot()) {
            try (var ignored2 = database.open()) {
                queue = database.read(() -> pendingUpdateRepository.findAll(this::decodeDetails));
            }
        }

        var thread = new Thread(this::refresh);
        thread.setName("update-queue-refresh");
        thread.start();
    }

    public void offer(String nodeName, SearchContentUpdateType type, Object details, String jobKey) {
        offer(new PendingUpdate(UUID.randomUUID(), nodeName, type, details, Instant.now(), jobKey));
    }

    public void offer(PendingUpdate update) {
        synchronized (lock) {
            queue.add(update);
        }
        database.writeNoResult(() -> {
            try {
                pendingUpdateRepository.create(update);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Cannot serialize the update object", e);
            }
        });
    }

    private Object decodeDetails(SearchContentUpdateType type, String encoded) {
        Class<?> detailsClass = getDetailsClass(type);
        if (detailsClass == null) {
            return null;
        }
        try {
            return objectMapper.readValue(encoded, detailsClass);
        } catch (JsonProcessingException e) {
            log.error(
                "Error decoding update details (type = {}, details = {})",
                LogUtil.format(type.getValue()), LogUtil.format(encoded), e
            );
            return null;
        }
    }

    private static Class<?> getDetailsClass(SearchContentUpdateType type) {
        return switch (type) {
            case PROFILE -> null;
            case FRIEND, UNFRIEND -> SearchFriendUpdate.class;
            case SUBSCRIBE, UNSUBSCRIBE -> SearchSubscriptionUpdate.class;
            case BLOCK, UNBLOCK -> SearchBlockUpdate.class;
            case POSTING_ADD, POSTING_UPDATE, POSTING_DELETE -> SearchPostingUpdate.class;
        };
    }

    private boolean prepared(PendingUpdate update) {
        return switch (update.type()) {
            case FRIEND, UNFRIEND, SUBSCRIBE, UNSUBSCRIBE, BLOCK, UNBLOCK ->
                database.read(() -> nodeRepository.isPeopleScanned(update.nodeName()));
            case POSTING_ADD, POSTING_UPDATE -> {
                SearchPostingUpdate details = (SearchPostingUpdate) update.details();
                // if posting deletion happened shortly before posting creation, the (:Posting) node could disappear
                yield postingIngest.newPosting(details.getNodeName(), details.getPostingId());
            }
            default -> true;
        };
    }

    private void start(PendingUpdate update) {
        switch (update.type()) {
            case FRIEND:
            case UNFRIEND: {
                var details = (SearchFriendUpdate) update.details();
                jobs.run(
                    FriendshipJob.class,
                    new FriendshipJob.Parameters(
                        update.type() == SearchContentUpdateType.UNFRIEND,
                        update.nodeName(),
                        details.getNodeName()
                    )
                );
                break;
            }

            case SUBSCRIBE:
            case UNSUBSCRIBE: {
                var details = (SearchSubscriptionUpdate) update.details();
                jobs.run(
                    SubscriptionJob.class,
                    new SubscriptionJob.Parameters(
                        update.type() == SearchContentUpdateType.UNSUBSCRIBE,
                        update.nodeName(),
                        details.getNodeName(),
                        details.getFeedName()
                    )
                );
                break;
            }

            case BLOCK:
            case UNBLOCK: {
                var details = (SearchBlockUpdate) update.details();
                jobs.run(
                    BlockingJob.class,
                    new BlockingJob.Parameters(
                        update.type() == SearchContentUpdateType.UNBLOCK,
                        update.nodeName(),
                        details.getNodeName(),
                        details.getBlockedOperation()
                    )
                );
                break;
            }

            case POSTING_ADD:
            case POSTING_UPDATE: {
                var details = (SearchPostingUpdate) update.details();
                jobs.run(
                    PostingUpdateJob.class,
                    new PostingUpdateJob.Parameters(update.nodeName(), details)
                );
                break;
            }

            case POSTING_DELETE: {
                var details = (SearchPostingUpdate) update.details();
                jobs.run(
                    PostingDeleteJob.class,
                    new PostingDeleteJob.Parameters(update.nodeName(), details)
                );
                break;
            }
        }

        database.writeNoResult(() -> pendingUpdateRepository.deleteById(update.id()));
    }

    private void refresh() {
        while (true) {
            try {
                Thread.sleep(Workload.UPDATE_QUEUE_JOB_START_PERIOD);
                if (database.isReady() && jobs.isReady() && !queue.isEmpty()) {
                    try (var ignored = requestCounter.allot()) {
                        try (var ignored2 = database.open()) {
                            processQueue();
                        }
                    }
                }
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                log.error("Error processing update queue", e);
            }
        }
    }

    private void processQueue() {
        var busy = new HashSet<String>();
        int i = 0;
        while (true) {
            PendingUpdate update;
            synchronized (lock) {
                if (i >= queue.size()) {
                    return;
                }
                update = queue.get(i);
            }
            boolean ready =
                !busy.contains(update.jobKey())
                && !database.read(() -> jobs.keyExists(update.jobKey()))
                && prepared(update);
            if (ready) {
                start(update);
                synchronized (lock) {
                    queue.remove(i);
                }
            } else {
                i++;
            }
            busy.add(update.jobKey());
        }
    }

}
