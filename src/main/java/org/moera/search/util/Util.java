package org.moera.search.util;

import java.time.Instant;

public class Util {

    public static long now() {
        return Instant.now().getEpochSecond();
    }

}
