package org.moera.search.job;

public class JobsManagerNotInitializedException extends RuntimeException {

    public JobsManagerNotInitializedException() {
        super("Jobs manager is not initialized yet");
    }

}
