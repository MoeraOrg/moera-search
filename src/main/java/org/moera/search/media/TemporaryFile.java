package org.moera.search.media;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record TemporaryFile(Path path, OutputStream outputStream) implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TemporaryFile.class);

    @Override
    public void close() {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Error removing temporary media file {}: {}", path, e.getMessage());
        }
    }

}
