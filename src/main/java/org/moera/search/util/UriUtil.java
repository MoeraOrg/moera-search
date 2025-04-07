package org.moera.search.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.util.ObjectUtils;

public class UriUtil {

    public static InetAddress remoteAddress(HttpServletRequest request) throws UnknownHostException {
        String forwardedAddress = request.getHeader("X-Forwarded-For");
        return InetAddress.getByName(
            !ObjectUtils.isEmpty(forwardedAddress)
                ? forwardedAddress
                : request.getRemoteAddr()
        );
    }

    public static String normalize(String uri) {
        if (uri == null) {
            return null;
        }
        return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    }

}
