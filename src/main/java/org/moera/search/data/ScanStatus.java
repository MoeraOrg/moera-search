package org.moera.search.data;

public record ScanStatus(ScanStatusType type, int retries) {

    public ScanStatus(Object object) {
        this(ScanStatusType.fromObject(object), object instanceof Long l ? l.intValue() : 0);
    }

    public Object toObject() {
        return switch (type) {
            case TODO -> null;
            case SUCCEEDED -> Boolean.TRUE;
            case FAILED -> Boolean.FALSE;
            case RETRY -> retries;
        };
    }

}
