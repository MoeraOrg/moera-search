package org.moera.search.media;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.io.output.ThresholdingOutputStream;

public class BoundedOutputStream extends ThresholdingOutputStream {

    private final OutputStream out;

    public BoundedOutputStream(OutputStream out, int threshold) {
        super(threshold);
        this.out = out;
    }

    @Override
    protected OutputStream getOutputStream() {
        return out;
    }

    @Override
    protected void thresholdReached() throws IOException {
        throw new ThresholdReachedException();
    }

}
