package org.moera.search.api;

import org.moera.lib.node.exception.MoeraNodeException;

public class MoeraNodeUncheckedException extends RuntimeException {

    private final MoeraNodeException exception;

    public MoeraNodeUncheckedException(MoeraNodeException exception) {
        super(exception);
        this.exception = exception;
    }

    public MoeraNodeException getException() {
        return exception;
    }

}
