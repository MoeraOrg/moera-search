package org.moera.search.data;

public enum ScanStatusType {

    TODO, SUCCEEDED, FAILED, RETRY;

    static ScanStatusType fromObject(Object object) {
        if (object == null) {
            return TODO;
        }
        if (object instanceof Boolean success) {
            return success ? SUCCEEDED : FAILED;
        }
        if (object instanceof Long) {
            return RETRY;
        }
        throw new IllegalArgumentException(
            "Cannot convert " + object.getClass().getCanonicalName() + " to ScanStatusType"
        );
    }

}
