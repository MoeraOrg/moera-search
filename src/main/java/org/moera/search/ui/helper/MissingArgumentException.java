package org.moera.search.ui.helper;

public class MissingArgumentException extends RuntimeException {

    public MissingArgumentException(String paramName) {
        super(getMessageText(paramName));
    }

    public MissingArgumentException(String paramName, Throwable cause) {
        super(getMessageText(paramName), cause);
    }

    private static String getMessageText(String paramName) {
        return "Missing required parameter '%s'".formatted(paramName);
    }

}
