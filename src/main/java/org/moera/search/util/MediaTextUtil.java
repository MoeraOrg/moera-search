package org.moera.search.util;

import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.moera.lib.node.types.MediaAttachment;
import org.moera.lib.node.types.PrivateMediaFileInfo;
import org.moera.lib.node.types.RemoteMediaInfo;
import org.springframework.util.ObjectUtils;

public final class MediaTextUtil {

    private MediaTextUtil() {
    }

    public static String buildMediaText(String title, String textContent) {
        var joiner = new StringJoiner(" ");
        if (!ObjectUtils.isEmpty(title)) {
            joiner.add(title);
        }
        if (!ObjectUtils.isEmpty(textContent)) {
            joiner.add(textContent);
        }
        return joiner.toString();
    }

    public static String buildMediaText(PrivateMediaFileInfo mediaInfo) {
        if (mediaInfo == null) {
            return "";
        }
        return buildMediaText(mediaInfo.getTitle(), mediaInfo.getTextContent());
    }

    public static String buildMediaText(RemoteMediaInfo mediaInfo) {
        if (mediaInfo == null) {
            return "";
        }
        return buildMediaText(mediaInfo.getTitle(), null);
    }

    public static String buildMediaText(MediaAttachment attachment) {
        if (attachment == null) {
            return "";
        }
        return attachment.getMedia() != null
            ? buildMediaText(attachment.getMedia())
            : buildMediaText(attachment.getRemoteMedia());
    }

    public static String buildMediaText(List<MediaAttachment> media) {
        if (ObjectUtils.isEmpty(media)) {
            return "";
        }

        return media.stream()
            .map(MediaTextUtil::buildMediaText)
            .filter(text -> !ObjectUtils.isEmpty(text))
            .collect(Collectors.joining(" "));
    }

}
