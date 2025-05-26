package org.moera.search.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;

import com.github.jknack.handlebars.Handlebars.SafeString;
import org.springframework.web.util.HtmlUtils;

public class Util {

    private static final String LUCENE_SPECIAL_CHARS = "+-&|!(){}[]^\"~*?:\\/=";

    public static Timestamp toTimestamp(Long epochSecond) {
        return epochSecond != null ? Timestamp.from(Instant.ofEpochSecond(epochSecond)) : null;
    }

    public static String ue(Object s) {
        return URLEncoder.encode(s.toString(), StandardCharsets.UTF_8);
    }

    public static SafeString he(Object s) {
        if (s == null) {
            return new SafeString("");
        }
        return s instanceof SafeString ss ? ss : new SafeString(HtmlUtils.htmlEscape(s.toString()));
    }

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

    public static String escapeLucene(String s) {
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (LUCENE_SPECIAL_CHARS.indexOf(s.charAt(i)) >= 0) {
                buf.append('\\');
            }
            buf.append(s.charAt(i));
        }
        return buf.toString();
    }

    public static String clearHtml(String s) {
        if (s == null) {
            return null;
        }
        return HtmlUtils.htmlUnescape(s.replaceAll("(?i)</?[a-z][^>]*>", " "));
    }

    public static Integer toInteger(Long value) {
        return value != null ? value.intValue() : null;
    }

    public static Short toShort(Long value) {
        return value != null ? value.shortValue() : null;
    }

}
