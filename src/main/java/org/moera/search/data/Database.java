package org.moera.search.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.function.Supplier;
import jakarta.inject.Inject;

import org.moera.search.config.Config;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class Database {

    public class SessionCloseable implements AutoCloseable {

        @Override
        public void close() {
            Database.this.close();
        }

    }

    private static final Logger log = LoggerFactory.getLogger(Database.class);

    @Inject
    private Config config;

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private ApplicationEventPublisher applicationEventPublisher;

    private Driver driver;
    private boolean ready = false;
    private final ThreadLocal<Session> session = new ThreadLocal<>();
    private final ThreadLocal<TransactionContext> tx = new ThreadLocal<>();

    @EventListener(ApplicationReadyEvent.class)
    private void init() {
        driver = GraphDatabase.driver(
            config.getDatabase().getUrl(),
            AuthTokens.basic(config.getDatabase().getUser(), config.getDatabase().getPassword())
        );
        driver.verifyConnectivity();
        log.info("Connected to database {}", config.getDatabase().getUrl());
        try {
            executeMigrations();
        } catch (Exception e) {
            throw new DatabaseException("Migration failed: " + e.getMessage(), e);
        }
        ready = true;
        applicationEventPublisher.publishEvent(new DatabaseInitializedEvent(this));
    }

    private void executeMigrations() throws IOException {
        try (var ignored = open()) {
            int version = getVersion();
            log.info("Current database version: {}", version);
            while (true) {
                var resources = applicationContext.getResources(
                    "classpath:/db/migration/V%d__*.cypher".formatted(++version)
                );
                if (resources.length == 0 || !resources[0].exists()) {
                    break;
                }
                executeMigration(version, resources[0]);
            }
        }
    }

    private void executeMigration(int version, Resource resource) throws IOException {
        log.info("Executing database migration {}", resource.getFilename());
        try (var reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            var buf = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                buf.append(line).append('\n');
            }
            executeWriteWithoutResult(() ->
                tx().run(buf.toString())
            );
            setVersion(version);
        }
    }

    private Integer getVersion() {
        return executeWrite(() ->
            tx().run(
                """
                MERGE (v:Version)
                    ON CREATE
                        SET v.version = 0
                RETURN v.version AS version
                """
            ).single().get("version").asInt()
        );
    }

    private void setVersion(int version) {
        executeWriteWithoutResult(() ->
            tx().run(
                """
                MATCH (v:Version)
                SET v.version = $version
                """,
                Map.of("version", version)
            )
        );
    }

    public boolean isReady() {
        return ready;
    }

    public SessionCloseable open() {
        if (session.get() != null) {
            throw new DatabaseException("Database session is open already");
        }
        session.set(driver.session(SessionConfig.forDatabase(config.getDatabase().getDatabase())));
        return new SessionCloseable();
    }

    public void close() {
        if (session.get() == null) {
            throw new DatabaseException("Database session is not open");
        }
        session.get().close();
        session.remove();
    }

    public Session session() {
        if (session.get() == null) {
            throw new DatabaseException("Database session is not open");
        }
        return session.get();
    }

    public <T> T executeRead(Supplier<T> callback) {
        if (tx.get() != null) {
            throw new DatabaseException("Transaction is running already");
        }
        return session().executeRead(context -> {
            tx.set(context);
            try {
                return callback.get();
            } finally {
                tx.remove();
            }
        });
    }

    public <T> T executeWrite(Supplier<T> callback) {
        if (tx.get() != null) {
            throw new DatabaseException("Transaction is running already");
        }
        return session().executeWrite(context -> {
            tx.set(context);
            try {
                return callback.get();
            } finally {
                tx.remove();
            }
        });
    }

    public void executeWriteWithoutResult(Runnable callback) {
        if (tx.get() != null) {
            throw new DatabaseException("Transaction is running already");
        }
        session().executeWriteWithoutResult(context -> {
            tx.set(context);
            try {
                callback.run();
            } finally {
                tx.remove();
            }
        });
    }

    public TransactionContext tx() {
        if (tx.get() == null) {
            throw new DatabaseException("Transaction is not running");
        }
        return tx.get();
    }

}
