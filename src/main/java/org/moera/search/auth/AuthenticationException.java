package org.moera.search.auth;

public class AuthenticationException extends RuntimeException {

    public AuthenticationException() {
        super("Authentication required");
    }

}
