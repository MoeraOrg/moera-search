package org.moera.search.api.fingerprint;

import java.util.function.BiFunction;

import org.moera.lib.crypto.CryptoUtil;
import org.moera.lib.crypto.FingerprintException;
import org.moera.lib.node.Fingerprints;
import org.moera.lib.node.types.CommentInfo;
import org.moera.lib.node.types.CommentRevisionInfo;
import org.moera.search.util.Util;

public class CommentFingerprintBuilder {

    public static final short LATEST_VERSION = 1;

    public static byte[] build(
        short version,
        CommentInfo commentInfo,
        BiFunction<String, String, byte[]> mediaDigest,
        byte[] postingDigest,
        byte[] repliedToDigest
    ) {
        return switch (version) {
            case 1 ->
                Fingerprints.comment1(
                    commentInfo.getOwnerName(),
                    postingDigest,
                    repliedToDigest,
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
            case 0 ->
                Fingerprints.comment0(
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
            default -> throw new FingerprintException("Unknown fingerprint version: " + version);
        };
    }

    public static byte[] build(
        short version,
        CommentInfo commentInfo,
        CommentRevisionInfo commentRevisionInfo,
        BiFunction<String, String, byte[]> mediaDigest,
        byte[] postingDigest,
        byte[] repliedToDigest
    ) {
        return switch (version) {
            case 1 ->
                Fingerprints.comment1(
                    commentInfo.getOwnerName(),
                    postingDigest,
                    repliedToDigest,
                    commentRevisionInfo.getBodySrcHash(),
                    commentRevisionInfo.getBodySrcFormat().getValue(),
                    commentRevisionInfo.getBody().getEncoded(),
                    commentRevisionInfo.getBodyFormat().getValue(),
                    Util.toTimestamp(commentRevisionInfo.getCreatedAt()),
                    (byte) 0,
                    CryptoUtil.digest(
                        AttachmentFingerprintBuilder.build(null, commentRevisionInfo.getMedia(), mediaDigest)
                    )
                );
            case 0 ->
                Fingerprints.comment0(
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
                        AttachmentFingerprintBuilder.build(null, commentRevisionInfo.getMedia(), mediaDigest)
                    )
                );
            default -> throw new FingerprintException("Unknown fingerprint version: " + version);
        };
    }

    private static byte[] nullDigest(byte[] digest) {
        return digest != null ? digest : CryptoUtil.digest(CryptoUtil.fingerprint(null));
    }

}
