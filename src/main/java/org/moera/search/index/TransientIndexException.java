package org.moera.search.index;

public class TransientIndexException extends RuntimeException {

    public TransientIndexException(Throwable cause) {
        super("Transient error while connecting the OpenSearch index", cause);
    }

}
