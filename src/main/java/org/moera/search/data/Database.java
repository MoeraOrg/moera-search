package org.moera.search.data;

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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class Database {

    private static final Logger log = LoggerFactory.getLogger(Database.class);

    @Inject
    private Config config;

    @Inject
    private ApplicationEventPublisher applicationEventPublisher;

    private Driver driver;
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
        executeMigrations();
        applicationEventPublisher.publishEvent(new DatabaseInitializedEvent(this));
    }

    private void executeMigrations() {
        try (Session session = open()) {
            int version = session.executeWrite(tx ->
                tx.run("""
                    MERGE (v:Version)
                        ON CREATE
                            SET v.version = 0
                    RETURN v.version AS version
                """).single().get("version").asInt()
            );
            log.info("Database version: {}", version);
        }
    }

    public Session open() {
        if (session.get() != null) {
            throw new DatabaseException("Database session is open already");
        }
        session.set(driver.session(SessionConfig.forDatabase(config.getDatabase().getDatabase())));
        return session.get();
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
            T result = callback.get();
            tx.remove();
            return result;
        });
    }

    public <T> T executeWrite(Supplier<T> callback) {
        if (tx.get() != null) {
            throw new DatabaseException("Transaction is running already");
        }
        return session().executeWrite(context -> {
            tx.set(context);
            T result = callback.get();
            tx.remove();
            return result;
        });
    }

    public void executeWriteWithoutResult(Runnable callback) {
        if (tx.get() != null) {
            throw new DatabaseException("Transaction is running already");
        }
        session().executeWriteWithoutResult(context -> {
            tx.set(context);
            callback.run();
            tx.remove();
        });
    }

    public TransactionContext tx() {
        if (tx.get() == null) {
            throw new DatabaseException("Transaction is not running");
        }
        return tx.get();
    }

}
