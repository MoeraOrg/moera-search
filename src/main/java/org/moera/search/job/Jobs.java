package org.moera.search.job;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.util.LogUtil;
import org.moera.search.data.Database;
import org.moera.search.data.DatabaseInitializedEvent;
import org.moera.search.data.JobRepository;
import org.moera.search.data.PendingJob;
import org.moera.search.global.RequestCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class Jobs {

    private static final Logger log = LoggerFactory.getLogger(Jobs.class);

    private final Map<UUID, Job<?, ?>> all = new ConcurrentHashMap<>();
    private final BlockingQueue<Job<?, ?>> pending =
            new PriorityBlockingQueue<>(8, Comparator.comparing(Job::getWaitUntil));

    private boolean ready = false;

    @Inject
    private ApplicationEventPublisher applicationEventPublisher;

    @Inject
    private RequestCounter requestCounter;

    @Inject
    private Database database;

    @Inject
    private JobRepository jobRepository;

    @Inject
    private ObjectMapper objectMapper;

    @Inject
    @Qualifier("jobTaskExecutor")
    private TaskExecutor taskExecutor;

    @Inject
    private AutowireCapableBeanFactory autowireCapableBeanFactory;

    @EventListener(DatabaseInitializedEvent.class)
    public void init() {
        ready = true;
        load();
        applicationEventPublisher.publishEvent(new JobsManagerInitializedEvent(this));
    }

    public boolean isReady() {
        return ready;
    }

    public <P, T extends Job<P, ?>> void run(Class<T> klass, P parameters) {
        run(klass, parameters, true);
    }

    public <P, T extends Job<P, ?>> void runNoPersist(Class<T> klass, P parameters) {
        run(klass, parameters, false);
    }

    private <P, T extends Job<P, ?>> void run(Class<T> klass, P parameters, boolean persistent) {
        if (!ready) {
            throw new JobsManagerNotInitializedException();
        }

        T job = null;
        try {
            job = klass.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            log.error("Cannot create a job", e);
        } catch (NoSuchMethodException e) {
            log.error("Cannot find a job constructor", e);
        }
        if (job == null) {
            return;
        }

        job.setParameters(parameters);
        job.setJobs(this);

        if (persistent) {
            persist(job);
            if (job.getId() != null) {
                all.put(job.getId(), job);
            }
        }

        autowireCapableBeanFactory.autowireBean(job);
        try {
            taskExecutor.execute(job);
        } catch (RejectedExecutionException e) {
            // ignore, the job was persisted
        }
    }

    @Scheduled(fixedDelayString = "PT1H")
    public void load() {
        if (!ready) {
            return;
        }

        try (var ignored = requestCounter.allot()) {
            try (var ignored2 = database.open()) {
                log.info("Loading pending jobs");

                long timestamp = Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli();
                database.executeRead(() -> jobRepository.findAllBefore(timestamp)).forEach(this::load);
            }
        }
    }

    private void load(PendingJob pendingJob) {
        if (all.containsKey(pendingJob.getId())) {
            return;
        }

        Job<?, ?> job = null;
        try {
            job = (Job<?, ?>) Class.forName(pendingJob.getJobType()).getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            log.error("Cannot create a job", e);
        } catch (NoSuchMethodException e) {
            log.error("Cannot find a job constructor", e);
        } catch (ClassNotFoundException e) {
            log.error("Cannot find a job class", e);
        }
        if (job == null) {
            return;
        }

        try {
            job.setParameters(pendingJob.getParameters(), objectMapper);
            if (pendingJob.getState() != null) {
                job.setState(pendingJob.getState(), objectMapper);
            }
        } catch (JsonProcessingException e) {
            log.error("Cannot load a job", e);
        }

        job.setId(pendingJob.getId());
        job.setRetries(pendingJob.getRetries());
        job.setWaitUntil(pendingJob.getWaitUntil() != null ? Instant.ofEpochMilli(pendingJob.getWaitUntil()) : null);
        job.setJobs(this);

        autowireCapableBeanFactory.autowireBean(job);

        all.put(job.getId(), job);
        if (job.getWaitUntil() != null && job.getWaitUntil().isAfter(Instant.now())) {
            pending.add(job);
        } else {
            try {
                taskExecutor.execute(job);
            } catch (Exception e) {
                // No space in the executor, wait a bit
                pending.add(job);
            }
        }
    }

    private void persist(Job<?, ?> job) {
        try {
            String parameters = objectMapper.writeValueAsString(job.getParameters());
            String state = job.getState() != null ? objectMapper.writeValueAsString(job.getState()) : null;
            var id = database.executeWrite(
                () -> jobRepository.create(job.getClass().getCanonicalName(), parameters, state)
            );
            job.setId(id);
        } catch (Exception e) {
            log.error("Error storing job", e);
        }
    }

    private void update(Job<?, ?> job) {
        if (job.getId() == null) {
            return;
        }
        try {
            String state = job.getState() != null ? objectMapper.writeValueAsString(job.getState()) : null;
            Long waitUntil = job.getWaitUntil() != null ? job.getWaitUntil().toEpochMilli() : null;
            database.executeWriteWithoutResult(
                () -> jobRepository.updateState(job.getId(), state, job.getRetries(), waitUntil)
            );
        } catch (Exception e) {
            log.error("Error saving job {}", LogUtil.format(job.getId()), e);
        }
    }

    void done(Job<?, ?> job) {
        if (job.getId() == null) {
            return;
        }
        all.remove(job.getId());
        try {
            database.executeWriteWithoutResult(
                () -> jobRepository.delete(job.getId())
            );
        } catch (Exception e) {
            log.error("Error deleting job {}", LogUtil.format(job.getId()), e);
        }
    }

    void checkpoint(Job<?, ?> job) {
        update(job);
    }

    void retrying(Job<?, ?> job) {
        if (job.getId() == null || job.getWaitUntil().isBefore(Instant.now().plus(1, ChronoUnit.HOURS))) {
            pending.add(job);
        } // otherwise, it will be destroyed and reconstructed from the database when retry time arrives
        update(job);
    }

    @Scheduled(fixedDelayString = "PT10S")
    public void restartPending() {
        var job = pending.peek();
        while (job != null && job.getWaitUntil().isBefore(Instant.now())) {
            pending.remove();
            try {
                taskExecutor.execute(job);
            } catch (RejectedExecutionException e) {
                // No space in the executor, wait a bit
                pending.add(job);
                return;
            }
            job = pending.peek();
        }
    }

}
