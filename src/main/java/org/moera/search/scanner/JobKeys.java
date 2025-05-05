package org.moera.search.scanner;

public class JobKeys {

    public static String nodeRelatives(String nodeName) {
        return "relative:" + nodeName;
    }

    public static String allContent(String nodeName) {
        return "entry:" + nodeName + ":all";
    }

    public static String anyContent(String nodeName) {
        return "entry:" + nodeName + ":*";
    }

    public static String posting(String nodeName, String postingId) {
        return "entry:" + nodeName + ":p-" + postingId;
    }

    public static String postingAllChildren(String nodeName, String postingId) {
        return "entry:" + nodeName + ":p-" + postingId + ":all";
    }

    public static String postingAllComments(String nodeName, String postingId) {
        return "entry:" + nodeName + ":p-" + postingId + ":all-comments";
    }

    public static String postingAllReactions(String nodeName, String postingId) {
        return "entry:" + nodeName + ":p-" + postingId + ":all-reactions";
    }

    public static String postingAnyChildren(String nodeName, String postingId) {
        return "entry:" + nodeName + ":p-" + postingId + ":*";
    }

    public static String comment(String nodeName, String postingId, String commentId) {
        return "entry:" + nodeName + ":p-" + postingId + ":c-" + commentId;
    }

    public static String commentAllChildren(String nodeName, String postingId, String commentId) {
        return "entry:" + nodeName + ":p-" + postingId + ":c-" + commentId + ":all";
    }

    public static String commentAnyChildren(String nodeName, String postingId, String commentId) {
        return "entry:" + nodeName + ":p-" + postingId + ":c-" + commentId + ":*";
    }

    public static String sheriff(String nodeName) {
        return "sheriff:" + nodeName;
    }

}
