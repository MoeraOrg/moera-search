package org.moera.search.data;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.util.LogUtil;
import org.moera.search.job.Job;
import org.moera.search.job.Jobs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PendingUpdate<P> {

    private static final Logger log = LoggerFactory.getLogger(PendingUpdate.class);

    private UUID id;
    private P jobParameters;
    private Instant createdAt;

    @Inject
    private ObjectMapper objectMapper;

    @Inject
    private Jobs jobs;

    public PendingUpdate() {
    }

    public PendingUpdate(P jobParameters) {
        id = UUID.randomUUID();
        this.jobParameters = jobParameters;
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    protected abstract Class<? extends Job<P, ?>> getJobClass();

    protected abstract Class<P> getJobParametersClass();

    public P getJobParameters() {
        return jobParameters;
    }

    public void setJobParameters(P jobParameters) {
        this.jobParameters = jobParameters;
    }

    public final void decodeJobParameters(String encoded) {
        if (encoded == null) {
            return;
        }
        try {
            setJobParameters(objectMapper.readValue(encoded, getJobParametersClass()));
        } catch (JsonProcessingException e) {
            log.error(
                "Error decoding job parameters (encoded = {}, class = {})",
                LogUtil.format(encoded), getJobParametersClass().getCanonicalName(), e
            );
        }
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isPrepared() {
        return true;
    }

    public List<String> waitJobKeys() {
        return Collections.emptyList();
    }

    public abstract String jobKey();

    public final void execute() {
        jobs.run(getJobClass(), jobParameters, jobKey());
    }

}
