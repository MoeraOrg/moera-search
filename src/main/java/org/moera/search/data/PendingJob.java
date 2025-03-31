package org.moera.search.data;

import java.util.UUID;

import org.neo4j.driver.types.Node;

public class PendingJob {

    private UUID id;
    private String jobType;
    private String parameters;
    private String state;
    private int retries;
    private Long waitUntil;

    public PendingJob(Node node) {
        id = UUID.fromString(node.get("id").asString(null));
        jobType = node.get("jobType").asString(null);
        parameters = node.get("parameters").asString(null);
        state = node.get("state").asString(null);
        retries = node.get("retries").asInt(0);
        waitUntil = (Long) node.get("waitUntil").asObject();
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
