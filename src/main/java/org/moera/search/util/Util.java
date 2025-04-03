package org.moera.search.util;

import java.util.Base64;

public class Util {

    public static byte[] hexdecode(String hex) {
        if (hex == null) {
            return null;
        }
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Input string must have an even length");
        }

        int len = hex.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            bytes[i / 2] = (byte) (
                (Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16)
            );
        }

        return bytes;
    }

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

    public static Integer toInteger(Long value) {
        return value != null ? value.intValue() : null;
    }

    public static Short toShort(Long value) {
        return value != null ? value.shortValue() : null;
    }

}
