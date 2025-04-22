package org.moera.search.scanner;

import java.time.Instant;
import java.util.UUID;

import org.moera.lib.node.types.SearchContentUpdateType;

public record PendingUpdate(
    UUID id,
    String nodeName,
    SearchContentUpdateType type,
    Object details,
    Instant createdAt,
    String jobKey
) {
}
