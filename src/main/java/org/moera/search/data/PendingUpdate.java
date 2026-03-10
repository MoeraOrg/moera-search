package org.moera.search.data;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import jakarta.inject.Inject;

import org.moera.search.job.Job;
import org.moera.search.job.Jobs;
import tools.jackson.databind.ObjectMapper;

public abstract class PendingUpdate<P> {

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
        setJobParameters(objectMapper.readValue(encoded, getJobParametersClass()));
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
