package org.moera.search.api;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import org.moera.lib.naming.MoeraNaming;
import org.moera.lib.naming.NodeName;
import org.moera.lib.naming.types.RegisteredNameInfo;
import org.moera.search.Workload;
import org.moera.search.config.Config;
import org.moera.search.global.RequestCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NamingCache {

    private static final class Record {

        public Instant accessed = Instant.now();
        public Instant deadline;
        public RegisteredNameDetails details;
        private Throwable error;

    }

    private static final Logger log = LoggerFactory.getLogger(NamingCache.class);

    private MoeraNaming naming;
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();
    private final Map<String, Record> cache = new HashMap<>();
    private final Object queryDone = new Object();

    @Inject
    private Config config;

    @Inject
    private RequestCounter requestCounter;

    @Inject
    @Qualifier("namingTaskExecutor")
    private TaskExecutor taskExecutor;

    @PostConstruct
    public void init() {
        naming = new MoeraNaming(config.getNamingServer());
    }

    public RegisteredNameDetails getFast(String name) {
        RegisteredNameDetails details = getOrRun(name);
        return details != null
            ? details.clone()
            : new RegisteredNameDetails(name, null, null);
    }

    public RegisteredNameDetails get(String name) {
        RegisteredNameDetails details = getOrRun(name);
        if (details != null) {
            return details.clone();
        }
        synchronized (queryDone) {
            while (true) {
                Record record = readRecord(name);
                if (record != null) {
                    if (record.error != null) {
                        throw new NamingNotAvailableException(record.error);
                    }
                    if (record.details != null) {
                        return record.details.clone();
                    }
                }
                try {
                    queryDone.wait();
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
    }

    private Record readRecord(String name) {
        cacheLock.readLock().lock();
        try {
            return cache.get(name);
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    private RegisteredNameDetails getOrRun(String name) {
        Record record = readRecord(name);
        if (record == null) {
            cacheLock.writeLock().lock();
            try {
                record = cache.get(name);
                if (record == null) {
                    cache.put(name, new Record());
                    run(name);
                    return null;
                }
            } finally {
                cacheLock.writeLock().unlock();
            }
        }
        record.accessed = Instant.now();
        if (record.error != null) {
            throw new NamingNotAvailableException(record.error);
        } else if (record.details != null) {
            return record.details.clone();
        } else {
            return null;
        }
    }

    private void run(String name) {
        taskExecutor.execute(() -> queryName(name));
    }

    private void queryName(String name) {
        NodeName registeredName = NodeName.parse(name);
        RegisteredNameInfo info = null;
        Throwable error = null;
        try {
            info = naming.getCurrent(registeredName.getName(), registeredName.getGeneration());
        } catch (Exception e) {
            error = e;
        }
        Record record = readRecord(name);
        if (record == null) {
            record = new Record();
            cacheLock.writeLock().lock();
            try {
                cache.put(name, record);
                if (registeredName.getGeneration() == 0) {
                    if (name.equals(registeredName.getName())) {
                        cache.put(registeredName.toString(), record);
                    } else {
                        cache.put(registeredName.getName(), record);
                    }
                }
            } finally {
                cacheLock.writeLock().unlock();
            }
        }
        record.details = info == null ? new RegisteredNameDetails() : new RegisteredNameDetails(info);
        record.error = error;
        record.deadline = Instant.now().plus(
            error == null ? Workload.NAMING_CACHE_NORMAL_TTL : Workload.NAMING_CACHE_ERROR_TTL
        );
        synchronized (queryDone) {
            queryDone.notifyAll();
        }
    }

    @Scheduled(fixedDelayString = Workload.NAMING_CACHE_PURGE_PERIOD)
    public void purge() {
        try (var ignored = requestCounter.allot()) {
            log.debug("Purging naming cache");

            List<String> remove;
            cacheLock.readLock().lock();
            try {
                remove = cache.entrySet().stream()
                    .filter(e -> e.getValue().deadline != null && e.getValue().deadline.isBefore(Instant.now()))
                    .map(Map.Entry::getKey)
                    .toList();
            } finally {
                cacheLock.readLock().unlock();
            }
            if (!remove.isEmpty()) {
                cacheLock.writeLock().lock();
                try {
                    remove.forEach(key -> {
                        if (cache.get(key).accessed.plus(Workload.NAMING_CACHE_NORMAL_TTL).isAfter(Instant.now())) {
                            run(key);
                        } else {
                            cache.remove(key);
                        }
                    });
                } finally {
                    cacheLock.writeLock().unlock();
                }
            }
        }
    }

}
