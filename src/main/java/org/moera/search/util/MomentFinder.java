package org.moera.search.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class MomentFinder {

    private final AtomicInteger nonce = new AtomicInteger(0);

    public long find(Predicate<Long> isFree, long momentBase) {
        int n = 0;
        while (true) {
            long moment = momentBase + nonce.getAndIncrement() % 1000;
            if (isFree.test(moment)) {
                return moment;
            }
            if (++n >= 1000) {
                n = 0;
                momentBase += 1000;
            }
        }

    }

}
