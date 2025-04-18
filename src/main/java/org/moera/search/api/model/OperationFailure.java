package org.moera.search.api.model;

import org.springframework.context.MessageSourceResolvable;

public class OperationFailure extends RuntimeException implements MessageSourceResolvable {

    private String errorCode;

    protected OperationFailure(String message, String errorCode) {
        super(message + ": " + errorCode);
        this.errorCode = errorCode;
    }

    public OperationFailure(String errorCode) {
        this("Operation failed", errorCode);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String[] getCodes() {
        return new String[] {errorCode};
    }

    @Override
    public Object[] getArguments() {
        return new Object[0];
    }

    @Override
    public String getDefaultMessage() {
        return getMessage();
    }

}
