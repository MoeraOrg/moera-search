package org.moera.search.media;

import java.io.OutputStream;
import java.nio.file.Path;

public record TemporaryFile(Path path, OutputStream outputStream) {
}
