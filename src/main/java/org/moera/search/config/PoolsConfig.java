package org.moera.search.config;

public class PoolsConfig {

    private int naming = 16;
    private int job = 16;

    public int getNaming() {
        return naming;
    }

    public void setNaming(int naming) {
        this.naming = naming;
    }

    public int getJob() {
        return job;
    }

    public void setJob(int job) {
        this.job = job;
    }

}
