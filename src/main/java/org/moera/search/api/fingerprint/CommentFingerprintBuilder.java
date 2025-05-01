package org.moera.search.api.fingerprint;

import java.util.function.Function;

import org.moera.lib.crypto.CryptoUtil;
import org.moera.lib.node.Fingerprints;
import org.moera.lib.node.types.CommentInfo;
import org.moera.lib.node.types.CommentRevisionInfo;
import org.moera.search.util.Util;

public class CommentFingerprintBuilder {

    public static final short LATEST_VERSION = 0;

    public static byte[] build(
        short version,
        CommentInfo commentInfo,
        Function<String, byte[]> mediaDigest,
        byte[] postingDigest,
        byte[] repliedToDigest
    ) {
        return Fingerprints.comment(
            commentInfo.getOwnerName(),
            postingDigest,
            nullDigest(repliedToDigest),
            commentInfo.getBodySrcHash(),
            commentInfo.getBodySrcFormat().getValue(),
            commentInfo.getBody().getEncoded(),
            commentInfo.getBodyFormat().getValue(),
            Util.toTimestamp(commentInfo.getRevisionCreatedAt()),
            (byte) 0,
            CryptoUtil.digest(
                AttachmentFingerprintBuilder.build(null, commentInfo.getMedia(), mediaDigest)
            )
        );
    }

    public static byte[] build(
        short version,
        CommentInfo commentInfo,
        CommentRevisionInfo commentRevisionInfo,
        Function<String, byte[]> mediaDigest,
        byte[] postingDigest,
        byte[] repliedToDigest
    ) {
        return Fingerprints.comment(
            commentInfo.getOwnerName(),
            postingDigest,
            nullDigest(repliedToDigest),
            commentRevisionInfo.getBodySrcHash(),
            commentRevisionInfo.getBodySrcFormat().getValue(),
            commentRevisionInfo.getBody().getEncoded(),
            commentRevisionInfo.getBodyFormat().getValue(),
            Util.toTimestamp(commentRevisionInfo.getCreatedAt()),
            (byte) 0,
            CryptoUtil.digest(
                AttachmentFingerprintBuilder.build(null, commentInfo.getMedia(), mediaDigest)
            )
        );
    }

    private static byte[] nullDigest(byte[] digest) {
        return digest != null ? digest : CryptoUtil.digest(CryptoUtil.fingerprint(null));
    }

}
