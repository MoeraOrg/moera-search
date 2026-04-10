package org.moera.search.util;

import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.moera.lib.node.types.MediaAttachment;
import org.moera.lib.node.types.PrivateMediaFileInfo;
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

    public static String buildMediaText(List<MediaAttachment> media) {
        if (ObjectUtils.isEmpty(media)) {
            return "";
        }

        return media.stream()
            .map(MediaAttachment::getMedia)
            .map(MediaTextUtil::buildMediaText)
            .filter(text -> !ObjectUtils.isEmpty(text))
            .collect(Collectors.joining(" "));
    }

}
