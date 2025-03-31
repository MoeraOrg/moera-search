package org.moera.search.auth;

public class IncorrectSignatureException extends RuntimeException {

    public IncorrectSignatureException() {
        super("Signature is incorrect");
    }

}
