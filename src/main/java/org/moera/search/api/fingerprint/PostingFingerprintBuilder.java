package org.moera.search.api.fingerprint;

import java.util.function.Function;

import org.moera.lib.crypto.CryptoUtil;
import org.moera.lib.crypto.FingerprintException;
import org.moera.lib.node.Fingerprints;
import org.moera.lib.node.types.PostingInfo;
import org.moera.lib.node.types.PostingRevisionInfo;
import org.moera.lib.node.types.SourceFormat;
import org.moera.search.util.Util;

public class PostingFingerprintBuilder {

    public static final short LATEST_VERSION = 1;

    private static boolean isOriginal(PostingInfo info) {
        return info.getReceiverName() == null;
    }

    public static byte[] build(
        short version,
        PostingInfo postingInfo,
        byte[] parentMediaDigest,
        Function<String, byte[]> mediaDigest
    ) {
        return switch (version) {
            case 1 ->
                Fingerprints.posting1(
                    postingInfo.getOwnerName(),
                    postingInfo.getOwnerName(),
                    postingInfo.getBodySrcHash(),
                    SourceFormat.toValue(postingInfo.getBodySrcFormat()),
                    postingInfo.getBody().getEncoded(),
                    postingInfo.getBodyFormat().getValue(),
                    Util.toTimestamp(
                        isOriginal(postingInfo)
                            ? postingInfo.getRevisionCreatedAt()
                            : postingInfo.getReceiverRevisionCreatedAt()
                    ),
                    (byte) 0,
                    CryptoUtil.digest(
                        AttachmentFingerprintBuilder.build(parentMediaDigest, postingInfo.getMedia(), mediaDigest)
                    )
                );
            case 0 ->
                Fingerprints.posting0(
                    postingInfo.getOwnerName(),
                    postingInfo.getOwnerName(),
                    postingInfo.getBodySrcHash(),
                    SourceFormat.toValue(postingInfo.getBodySrcFormat()),
                    postingInfo.getBody().getEncoded(),
                    postingInfo.getBodyFormat().getValue(),
                    Util.toTimestamp(
                        isOriginal(postingInfo)
                            ? postingInfo.getRevisionCreatedAt()
                            : postingInfo.getReceiverRevisionCreatedAt()
                    ),
                    (byte) 0,
                    (byte) 0
                );
            default -> throw new FingerprintException("Unknown fingerprint version: " + version);
        };
    }

    public static byte[] build(
        short version,
        PostingInfo postingInfo,
        PostingRevisionInfo postingRevisionInfo,
        byte[] parentMediaDigest,
        Function<String, byte[]> mediaDigest
    ) {
        return switch (version) {
            case 1 ->
                Fingerprints.posting1(
                    postingInfo.getOwnerName(),
                    postingInfo.getOwnerName(),
                    postingRevisionInfo.getBodySrcHash(),
                    SourceFormat.toValue(postingRevisionInfo.getBodySrcFormat()),
                    postingRevisionInfo.getBody().getEncoded(),
                    postingRevisionInfo.getBodyFormat().getValue(),
                    Util.toTimestamp(
                        isOriginal(postingInfo)
                            ? postingRevisionInfo.getCreatedAt()
                            : postingRevisionInfo.getReceiverCreatedAt()
                    ),
                    (byte) 0,
                    CryptoUtil.digest(
                        AttachmentFingerprintBuilder.build(
                            parentMediaDigest, postingRevisionInfo.getMedia(), mediaDigest
                        )
                    )
                );
            case 0 ->
                Fingerprints.posting0(
                    postingInfo.getOwnerName(),
                    postingInfo.getOwnerName(),
                    postingRevisionInfo.getBodySrcHash(),
                    SourceFormat.toValue(postingRevisionInfo.getBodySrcFormat()),
                    postingRevisionInfo.getBody().getEncoded(),
                    postingRevisionInfo.getBodyFormat().getValue(),
                    Util.toTimestamp(
                        isOriginal(postingInfo)
                            ? postingRevisionInfo.getCreatedAt()
                            : postingRevisionInfo.getReceiverCreatedAt()
                    ),
                    (byte) 0,
                    (byte) 0
                );
            default -> throw new FingerprintException("Unknown fingerprint version: " + version);
        };
    }

}
