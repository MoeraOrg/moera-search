package org.moera.search.job;

public class StopJobException extends RuntimeException {

    public StopJob type;

    public StopJobException(StopJob type) {
        this.type = type;
    }

    public StopJob getType() {
        return type;
    }

}
