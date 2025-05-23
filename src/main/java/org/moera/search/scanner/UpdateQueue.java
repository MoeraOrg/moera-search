package org.moera.search.scanner;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.moera.search.Workload;
import org.moera.search.data.Database;
import org.moera.search.data.DatabaseInitializedEvent;
import org.moera.search.data.PendingUpdate;
import org.moera.search.data.PendingUpdateRepository;
import org.moera.search.data.UpdateQueueInitializedEvent;
import org.moera.search.global.RequestCounter;
import org.moera.search.job.Jobs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class UpdateQueue {

    private static final Logger log = LoggerFactory.getLogger(UpdateQueue.class);

    private static final Duration UPDATE_TIMEOUT = Duration.ofHours(6);

    private List<PendingUpdate<?>> queue = new ArrayList<>();
    private final Object lock = new Object();

    @Inject
    private RequestCounter requestCounter;

    @Inject
    private Database database;

    @Inject
    private PendingUpdateRepository pendingUpdateRepository;

    @Inject
    private Jobs jobs;

    @Inject
    private AutowireCapableBeanFactory autowireCapableBeanFactory;

    @Inject
    private ApplicationEventPublisher applicationEventPublisher;

    @EventListener(DatabaseInitializedEvent.class)
    public void init() {
        try (var ignored = requestCounter.allot()) {
            try (var ignored2 = database.open()) {
                log.info("Loading the queue of updates");
                synchronized (lock) {
                    var stored = database.read(() -> pendingUpdateRepository.findAll());
                    stored.addAll(queue);
                    queue = stored;
                }
            }
        }

        var thread = new Thread(this::refresh);
        thread.setName("update-queue-refresh");
        thread.start();

        applicationEventPublisher.publishEvent(new UpdateQueueInitializedEvent(this));
    }

    public void offer(PendingUpdate<?> update) {
        synchronized (lock) {
            queue.add(update);
        }
        autowireCapableBeanFactory.autowireBean(update);
        database.writeNoResult(() -> {
            try {
                pendingUpdateRepository.create(update);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Cannot serialize the update object", e);
            }
        });
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
        int startedJobs = 0;
        int i = 0;
        while (true) {
            PendingUpdate<?> update;
            synchronized (lock) {
                if (i >= queue.size()) {
                    return;
                }
                update = queue.get(i);
            }
            var waitJobKeys = update.waitJobKeys();
            boolean ready =
                (
                    waitJobKeys == null
                    || waitJobKeys.stream().noneMatch(busy::contains)
                        && database.read(() -> waitJobKeys.stream().noneMatch(jobs::keyExists))
                )
                && update.isPrepared();
            if (ready) {
                update.execute();
                database.writeNoResult(() -> pendingUpdateRepository.deleteById(update.getId()));
                synchronized (lock) {
                    queue.remove(i);
                }
                if (++startedJobs >= Workload.UPDATE_QUEUE_MAX_STARTED_JOBS) {
                    return;
                }
            } else if (update.getCreatedAt().plus(UPDATE_TIMEOUT).isBefore(Instant.now())) {
                database.writeNoResult(() -> pendingUpdateRepository.deleteById(update.getId()));
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
