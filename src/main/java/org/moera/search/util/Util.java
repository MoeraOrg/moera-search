package org.moera.search.util;

import java.util.Base64;

public class Util {

    public static String base64encode(byte[] bytes) {
        return bytes != null ? Base64.getEncoder().encodeToString(bytes) : null;
    }

    public static byte[] base64decode(String s) {
        return s != null ? Base64.getDecoder().decode(s) : null;
    }

    public static String base64urlencode(byte[] bytes) {
        return bytes != null ? Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) : null;
    }

    public static byte[] base64urldecode(String s) {
        return s != null ? Base64.getUrlDecoder().decode(s) : null;
    }

}
