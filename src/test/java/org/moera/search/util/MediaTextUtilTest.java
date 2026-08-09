package org.moera.search.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.moera.lib.node.types.MediaAttachment;
import org.moera.lib.node.types.RemoteMediaInfo;

public class MediaTextUtilTest {

    @Test
    void remoteMediaTextIsIncluded() {
        RemoteMediaInfo remoteMediaInfo = new RemoteMediaInfo();
        remoteMediaInfo.setTitle("title");
        remoteMediaInfo.setTextContent("recognized text");

        MediaAttachment attachment = new MediaAttachment();
        attachment.setRemoteMedia(remoteMediaInfo);

        Assertions.assertEquals("recognized text", MediaAttachmentUtil.textContent(attachment));
        Assertions.assertEquals("title recognized text", MediaTextUtil.buildMediaText(attachment));
    }

}
