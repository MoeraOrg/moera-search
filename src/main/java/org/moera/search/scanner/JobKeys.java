package org.moera.search.scanner;

public class JobKeys {

    public static String nodeRelative(String nodeName) {
        return "relative:" + nodeName;
    }

    public static String posting(String nodeName, String postingId) {
        return "posting:" + nodeName + ":" + postingId;
    }

}
