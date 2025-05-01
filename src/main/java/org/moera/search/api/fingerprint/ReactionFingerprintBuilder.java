package org.moera.search.api.fingerprint;

import org.moera.lib.node.Fingerprints;
import org.moera.lib.node.types.ReactionInfo;

public class ReactionFingerprintBuilder {

    public static final short LATEST_VERSION = 0;

    public static byte[] build(
        short version,
        ReactionInfo reactionInfo,
        byte[] entryDigest
    ) {
        return Fingerprints.reaction(
            reactionInfo.getOwnerName(),
            entryDigest,
            Boolean.TRUE.equals(reactionInfo.getNegative()),
            reactionInfo.getEmoji()
        );
    }

}
