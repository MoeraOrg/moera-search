package org.moera.search.media;

import org.moera.lib.node.types.AvatarImage;
import org.moera.lib.util.LogUtil;
import org.moera.search.data.MediaFile;

public class AvatarImageUtil {

    public static AvatarImage build(MediaFile mediaFile, String shape) {
        AvatarImage avatarImage = new AvatarImage();
        setMediaFile(avatarImage, mediaFile);
        if (mediaFile != null) {
            avatarImage.setMediaId(mediaFile.getId());
            avatarImage.setPath("public/" + mediaFile.getFileName());
            avatarImage.setWidth(mediaFile.getSizeX());
            avatarImage.setHeight(mediaFile.getSizeY());
        }
        avatarImage.setShape(shape);
        return avatarImage;
    }

    public static MediaFile getMediaFile(AvatarImage avatarImage) {
        return (MediaFile) avatarImage.getExtra();
    }

    public static void setMediaFile(AvatarImage avatarImage, MediaFile mediaFile) {
        avatarImage.setExtra(mediaFile);
    }

    public static String toLogString(AvatarImage avatarImage) {
        return "AvatarImage(path=%s, shape=%s)".formatted(
            LogUtil.format(avatarImage.getPath()), LogUtil.format(avatarImage.getShape())
        );
    }

}
