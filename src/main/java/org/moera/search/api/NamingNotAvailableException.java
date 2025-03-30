package org.moera.search.api;

public class NamingNotAvailableException extends RuntimeException {

    public NamingNotAvailableException(Throwable e) {
        super("Naming server is not available", e);
    }

}
