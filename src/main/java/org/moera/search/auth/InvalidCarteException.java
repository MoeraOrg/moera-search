package org.moera.search.auth;

public class InvalidCarteException extends RuntimeException {

    private final String errorCode;

    public InvalidCarteException(String errorCode) {
        super("Invalid carte: " + errorCode);
        this.errorCode = errorCode;
    }

    public InvalidCarteException(String errorCode, Throwable e) {
        super("Invalid carte: " + errorCode, e);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

}
