package org.moera.search.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.moera.lib.node.types.MediaAttachment;
import org.moera.lib.node.types.PrivateMediaFileInfo;
import org.moera.lib.node.types.body.Body;
import org.moera.lib.node.types.body.LinkPreview;
import org.springframework.util.ObjectUtils;

public class BodyUtil {

    private static final Pattern VIDEO_TAGS = Pattern.compile("(?i)<(?:object|video|iframe)");
    private static final Pattern HASHTAGS = Pattern.compile("(?U)(?:^|[\\s(\\[{>])(#\\w+)\\b");

    public record BodyMediaCount(int imageCount, boolean videoPresent) {
    }

    public static BodyMediaCount countBodyMedia(Body body, List<MediaAttachment> media) {
        int imageCount = 0;
        if (media != null) {
            int linkMediaCount = 0;
            if (body.getLinkPreviews() != null) {
                linkMediaCount = (int) body.getLinkPreviews().stream().filter(lp -> lp.getImageHash() != null).count();
            }
            imageCount = media.size() - linkMediaCount;
        }
        boolean videoPresent = VIDEO_TAGS.matcher(body.getText()).find();

        return new BodyMediaCount(imageCount, videoPresent);
    }

    public static PrivateMediaFileInfo findMediaForPreview(Body body, List<MediaAttachment> media) {
        if (!ObjectUtils.isEmpty(media)) {
            var linkPreviews = !ObjectUtils.isEmpty(body.getLinkPreviews())
                ? body.getLinkPreviews().stream()
                    .map(LinkPreview::getImageHash)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet())
                : Collections.emptySet();
            for (var attachment : media) {
                if (attachment.getMedia() != null) {
                    if (!linkPreviews.contains(attachment.getMedia().getHash())) {
                        return attachment.getMedia();
                    }
                }
            }
        }
        return null;
    }

    public static List<String> extractHashtags(String text) {
        if (ObjectUtils.isEmpty(text)) {
            return Collections.emptyList();
        }
        var hashtags = new ArrayList<String>();
        var m = HASHTAGS.matcher(text);
        while (m.find()) {
            hashtags.add(m.group(1));
        }
        return hashtags;
    }

}
