package org.moera.search.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.moera.lib.node.types.MediaAttachment;
import org.moera.lib.node.types.body.Body;
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
