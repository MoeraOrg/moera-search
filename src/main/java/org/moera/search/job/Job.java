package org.moera.search.job;

import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.crypto.CryptoUtil;
import org.moera.lib.node.carte.Carte;
import org.moera.lib.node.exception.MoeraNodeApiAuthenticationException;
import org.moera.lib.node.exception.MoeraNodeApiNotFoundException;
import org.moera.lib.node.exception.MoeraNodeApiOperationException;
import org.moera.lib.node.exception.MoeraNodeApiValidationException;
import org.moera.lib.node.exception.MoeraNodeException;
import org.moera.lib.node.types.Scope;
import org.moera.search.api.MoeraNodeUnknownNameException;
import org.moera.search.config.Config;
import org.moera.search.config.NotConfiguredException;
import org.moera.search.data.Database;
import org.moera.search.global.RequestCounter;
import org.moera.search.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Job<P, S> implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(Job.class);

    protected P parameters;
    protected S state;

    protected Jobs jobs;

    @Inject
    protected Config config;

    @Inject
    protected Database database;

    private UUID id;
    private int retries;
    private Instant waitUntil;
    private JobRetryPolicy retryPolicy;

    @Inject
    private RequestCounter requestCounter;

    public Job() {
        exponentialRetry("PT10S", "PT3H");
    }

    public String getJobKey() {
        return null;
    }

    P getParameters() {
        return parameters;
    }

    void setParameters(P parameters) {
        this.parameters = parameters;
    }

    protected abstract void setParameters(String parameters, ObjectMapper objectMapper) throws JsonProcessingException;

    S getState() {
        return state;
    }

    protected abstract void setState(String state, ObjectMapper objectMapper) throws JsonProcessingException;

    void setJobs(Jobs jobs) {
        this.jobs = jobs;
    }

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    int getRetries() {
        return retries;
    }

    void setRetries(int retries) {
        this.retries = retries;
    }

    Instant getWaitUntil() {
        return waitUntil;
    }

    void setWaitUntil(Instant waitUntil) {
        this.waitUntil = waitUntil;
    }

    protected JobRetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    protected void setRetryPolicy(JobRetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    protected void noRetry() {
        setRetryPolicy(new JobNoRetryPolicy());
    }

    protected void retryCount(int maxCount, Duration period) {
        setRetryPolicy(new JobRetryCountPolicy(this, maxCount, period));
    }

    protected void retryCount(int maxCount, String period) {
        setRetryPolicy(new JobRetryCountPolicy(this, maxCount, period));
    }

    protected void exponentialRetry(Duration minPeriod, Duration maxPeriod) {
        setRetryPolicy(new JobExponentialRetryPolicy(this, minPeriod, maxPeriod));
    }

    protected void exponentialRetry(String minPeriod, String maxPeriod) {
        setRetryPolicy(new JobExponentialRetryPolicy(this, minPeriod, maxPeriod));
    }

    public final void success() {
        throw new StopJobException(StopJob.SUCCESS);
    }

    public final void fail() {
        throw new StopJobException(StopJob.FAILURE);
    }

    public final void retry() {
        throw new StopJobException(StopJob.RETRY);
    }

    public final void checkpoint() {
        retries = 0;
        waitUntil = null;
        jobs.checkpoint(this);
    }

    private void done() {
        jobs.done(this);
    }

    private void recover(Throwable e) {
        if (e != null) {
            log.error("Error executing job {}: {}", getJobDescription(), e.getMessage());
        }

        retries++;
        if (!retryPolicy.tryAgain()) {
            log.info("Giving up");
            failed();
            return;
        }
        Duration duration = retryPolicy.waitTime();
        log.info("Retrying in {}s", duration.toSeconds());
        waitUntil = Instant.now().plus(duration);
        jobs.retrying(this);
    }

    protected void handleException(Throwable e) {
        if (e instanceof StopJobException ex) {
            switch (ex.type) {
                case SUCCESS -> succeeded();
                case FAILURE -> failed();
                case RETRY -> recover(null);
            }
        } else if (e instanceof InterruptedException) {
            recover(e);
        } else if (e instanceof MoeraNodeException ex && isRecoverableError(ex)) {
            recover(e);
        } else {
            unhandledException(e);
        }
    }

    protected void unhandledException(Throwable e) {
        if (e instanceof MoeraNodeUnknownNameException ex) {
            log.error("Cannot find a node {}", ex.getNodeName());
        } else {
            log.error("Fatal error executing job {}: {}", getJobDescription(), e.getMessage());
            log.debug("Fatal error executing job:", e);
        }
        failed();
    }

    protected boolean isRecoverableError(MoeraNodeException e) {
        if (e instanceof MoeraNodeUnknownNameException) {
            return false;
        }
        if (e instanceof MoeraNodeApiValidationException) {
            return false;
        }
        if (e instanceof MoeraNodeApiAuthenticationException) {
            return false;
        }
        if (e instanceof MoeraNodeApiNotFoundException) {
            return false;
        }
        if (e instanceof MoeraNodeApiOperationException ex) {
            return !ex.getErrorCode().equals("ask.too-many");
        }
        return true;
    }

    @Override
    public final void run() {
        try (var ignored = requestCounter.allot()) {
            try (var ignored2 = database.open()) {
                started();

                boolean exceptionThrown = false;
                try {
                    execute();
                } catch (Throwable e) {
                    handleException(e);
                    exceptionThrown = true;
                } finally {
                    if (!exceptionThrown) {
                        succeeded();
                    }
                }
            }
        }
    }

    protected abstract void execute() throws Exception;

    protected void started() {
        if (retries == 0) {
            log.info("Executing job {}", getJobDescription());
        } else {
            log.info("Executing job {}, retry {}", getJobDescription(), retries);
        }
    }

    protected void succeeded() {
        log.info("Succeeded executing job {}", getJobDescription());
        done();
    }

    protected void failed() {
        done();
    }

    protected String getJobDescription() {
        return this.getClass().getSimpleName();
    }

    protected PrivateKey signingKey() {
        if (config.getSigningKey() == null) {
            throw new NotConfiguredException("Signing key is not set in the configuration file (node.signing-key)");
        }
        return CryptoUtil.rawToPrivateKey(Util.hexdecode(config.getSigningKey()));
    }

    protected String generateCarte(String targetNodeName, Scope clientScope) {
        return generateCarte(targetNodeName, clientScope, Scope.NONE);
    }

    protected String generateCarte(String targetNodeName, Scope clientScope, Scope adminScope) {
        try {
            return Carte.generate(
                config.getNodeName(), jobs.getLocalAddress(), Instant.now(), signingKey(), targetNodeName,
                clientScope.getMask(), adminScope.getMask()
            );
        } catch (Exception e) {
            log.error("Error generating carte");
            throw e;
        }
    }

}
