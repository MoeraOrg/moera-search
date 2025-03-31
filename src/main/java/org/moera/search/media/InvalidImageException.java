package org.moera.search.media;

import java.io.IOException;

public class InvalidImageException extends IOException {

    public InvalidImageException() {
        super("Invalid media file");
    }

}
