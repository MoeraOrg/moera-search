package org.moera.search.util;

import org.moera.lib.node.types.MediaAttachment;

public final class MediaAttachmentUtil {

    private MediaAttachmentUtil() {
    }

    public static String mediaId(MediaAttachment attachment) {
        return attachment.getMedia() != null
            ? attachment.getMedia().getId()
            : attachment.getRemoteMedia() != null
                ? attachment.getRemoteMedia().getMediaId()
                : null;
    }

    public static String nodeName(MediaAttachment attachment) {
        return nodeName(attachment, null);
    }

    public static String nodeName(MediaAttachment attachment, String localNodeName) {
        return attachment.getMedia() != null
            ? localNodeName
            : attachment.getRemoteMedia() != null
              ? attachment.getRemoteMedia().getNodeName()
              : null;
    }

    public static String hash(MediaAttachment attachment) {
        return attachment.getMedia() != null
            ? attachment.getMedia().getHash()
            : attachment.getRemoteMedia() != null
                ? attachment.getRemoteMedia().getHash()
                : null;
    }

    public static String mimeType(MediaAttachment attachment) {
        return attachment.getMedia() != null
            ? attachment.getMedia().getMimeType()
            : attachment.getRemoteMedia() != null
                ? attachment.getRemoteMedia().getMimeType()
                : null;
    }

    public static Long size(MediaAttachment attachment) {
        return attachment.getMedia() != null
            ? Long.valueOf(attachment.getMedia().getSize())
            : attachment.getRemoteMedia() != null
                ? attachment.getRemoteMedia().getSize()
                : null;
    }

    public static boolean attachment(MediaAttachment attachment) {
        return attachment.getMedia() != null
            ? Boolean.TRUE.equals(attachment.getMedia().getAttachment())
            : attachment.getRemoteMedia() != null && Boolean.TRUE.equals(attachment.getRemoteMedia().getAttachment());
    }

    public static String title(MediaAttachment attachment) {
        return attachment.getMedia() != null
            ? attachment.getMedia().getTitle()
            : attachment.getRemoteMedia() != null
                ? attachment.getRemoteMedia().getTitle()
                : null;
    }

    public static String textContent(MediaAttachment attachment) {
        return attachment.getMedia() != null ? attachment.getMedia().getTextContent() : null;
    }

}
