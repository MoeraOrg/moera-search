package org.moera.search.api.fingerprint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.moera.lib.node.Fingerprints;
import org.moera.lib.node.types.MediaAttachment;
import org.moera.lib.node.types.PrivateMediaFileInfo;

public class AttachmentFingerprintBuilder {

    public static final short LATEST_VERSION = 0;

    public static byte[] build(byte[] digest) {
        return build(LATEST_VERSION, digest);
    }

    public static byte[] build(short version, byte[] digest) {
        return Fingerprints.attachment(digest);
    }

    public static List<byte[]> build(
        byte[] parentMediaDigest,
        Collection<MediaAttachment> mediaAttachments,
        Function<String, byte[]> mediaDigest
    ) {
        if (mediaAttachments == null) {
            mediaAttachments = Collections.emptyList();
        }

        List<byte[]> digests = new ArrayList<>();
        if (parentMediaDigest != null) {
            digests.add(build(parentMediaDigest));
        }
        mediaAttachments.stream()
            .map(MediaAttachment::getMedia)
            .map(PrivateMediaFileInfo::getId)
            .map(mediaDigest)
            .map(AttachmentFingerprintBuilder::build)
            .forEach(digests::add);
        return digests;
    }

}
