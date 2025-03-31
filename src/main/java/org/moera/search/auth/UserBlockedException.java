package org.moera.search.auth;

public class UserBlockedException extends RuntimeException {

    public UserBlockedException() {
        super("Blocked from performing this operation");
    }

}
