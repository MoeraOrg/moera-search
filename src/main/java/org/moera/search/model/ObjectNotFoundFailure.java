package org.moera.search.model;

public class ObjectNotFoundFailure extends OperationFailure {

    public ObjectNotFoundFailure(String errorCode) {
        super("Object not found", errorCode);
    }

}
