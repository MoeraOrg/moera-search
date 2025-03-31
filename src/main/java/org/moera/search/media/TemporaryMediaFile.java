package org.moera.search.media;

public record TemporaryMediaFile(String mediaFileId, String contentType, byte[] digest) {
}
