package org.moera.search.util;

public class LockUnderflowException extends RuntimeException {

    public LockUnderflowException(String key) {
        super("Lock underflow for key %s".formatted(key));
    }

}
