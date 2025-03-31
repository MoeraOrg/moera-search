package org.moera.search.data;

import java.util.UUID;

import org.neo4j.driver.Record;

public class PendingJob {

    private UUID id;
    private String jobType;
    private String parameters;
    private String state;
    private int retries;
    private Long waitUntil;

    public PendingJob(Record record) {
        id = UUID.fromString(record.get("id").asString(null));
        jobType = record.get("jobType").asString(null);
        parameters = record.get("parameters").asString(null);
        state = record.get("state").asString(null);
        retries = record.get("retries").asInt(0);
        waitUntil = (Long) record.get("waitUntil").asObject();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public String getParameters() {
        return parameters;
    }

    public void setParameters(String parameters) {
        this.parameters = parameters;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public Long getWaitUntil() {
        return waitUntil;
    }

    public void setWaitUntil(Long waitUntil) {
        this.waitUntil = waitUntil;
    }

}
