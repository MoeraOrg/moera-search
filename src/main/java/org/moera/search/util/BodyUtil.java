package org.moera.search.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.moera.lib.node.types.MediaAttachment;
import org.moera.lib.node.types.body.Body;
import org.moera.lib.node.types.body.LinkPreview;
import org.springframework.util.ObjectUtils;

public class BodyUtil {

    private static final Pattern VIDEO_TAGS = Pattern.compile("(?i)<(?:object|video|iframe)");
    private static final Pattern HASHTAGS = Pattern.compile(
        "(?U)(?:^|[\\s(\\[{>])(#[\\p{L}\\p{Nd}_]*[\\p{L}_][\\p{L}\\p{Nd}_]*)\\b"
    );
    private static final List<String> VIDEO_FORMATS = List.of(
        "video/mp4",
        "video/quicktime",
        "video/webm",
        "video/ogg",
        "video/x-msvideo",
        "video/x-matroska",
        "video/mpeg",
        "video/mp2t",
        "video/3gpp",
        "video/3gpp2",
        "video/x-flv",
        "video/x-ms-wmv",
        "video/x-ms-asf",
        "video/x-m4v",
        "application/mp4",
        "application/ogg",
        "application/x-matroska",
        "application/vnd.rn-realmedia",
        "application/vnd.ms-asf"
    );

    public record BodyMediaCount(int imageCount, int attachmentCount, boolean videoPresent) {
    }

    public static BodyMediaCount countBodyMedia(Body body, List<MediaAttachment> media) {
        int imageCount = 0;
        int videoCount = 0;
        int attachmentCount = 0;
        var linkPreviews = !ObjectUtils.isEmpty(body.getLinkPreviews())
            ? body.getLinkPreviews().stream()
                .map(LinkPreview::getImageHash)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet())
            : Collections.<String>emptySet();
        if (media != null) {
            for (var attachment : media) {
                if (attachment.getMedia() == null && attachment.getRemoteMedia() == null) {
                    continue;
                }
                var hash = MediaAttachmentUtil.hash(attachment);
                if (hash != null && linkPreviews.contains(hash)) {
                    continue;
                }
                if (MediaAttachmentUtil.attachment(attachment)) {
                    attachmentCount++;
                } else if (VIDEO_FORMATS.contains(MediaAttachmentUtil.mimeType(attachment))) {
                    videoCount++;
                } else {
                    imageCount++;
                }
            }
        }
        boolean videoObjectPresent = body.getText() != null && VIDEO_TAGS.matcher(body.getText()).find();
        boolean videoPresent = videoCount > 0 || videoObjectPresent;

        return new BodyMediaCount(imageCount, attachmentCount, videoPresent);
    }

    public static MediaAttachment findMediaForPreview(Body body, List<MediaAttachment> media) {
        if (!ObjectUtils.isEmpty(media)) {
            var linkPreviews = !ObjectUtils.isEmpty(body.getLinkPreviews())
                ? body.getLinkPreviews().stream()
                    .map(LinkPreview::getImageHash)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet())
                : Collections.emptySet();
            MediaAttachment embeddedAttachment = null;
            for (var attachment : media) {
                if (
                    (attachment.getMedia() != null || attachment.getRemoteMedia() != null)
                    && !MediaAttachmentUtil.attachment(attachment)
                    && !linkPreviews.contains(MediaAttachmentUtil.hash(attachment))
                ) {
                    if (!attachment.isEmbedded()) {
                        return attachment;
                    }
                    if (embeddedAttachment == null) {
                        embeddedAttachment = attachment;
                    }
                }
            }
            return embeddedAttachment;
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
