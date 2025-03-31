package org.moera.search.media;

import java.io.IOException;

public class ThresholdReachedException extends IOException {

    public ThresholdReachedException() {
        super("Threshold reached");
    }

}
