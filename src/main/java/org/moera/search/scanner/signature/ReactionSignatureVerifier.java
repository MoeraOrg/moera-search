package org.moera.search.scanner.signature;

import jakarta.inject.Inject;

import org.moera.lib.crypto.CryptoUtil;
import org.moera.lib.node.exception.MoeraNodeException;
import org.moera.lib.node.types.ReactionInfo;
import org.moera.search.api.fingerprint.ReactionFingerprintBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ReactionSignatureVerifier extends SignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(ReactionSignatureVerifier.class);

    @Inject
    private PostingSignatureVerifier postingSignatureVerifier;

    @Inject
    private CommentSignatureVerifier commentSignatureVerifier;

    public void verifySignature(
        String nodeName,
        ReactionInfo reactionInfo,
        String carte
    ) throws MoeraNodeException {
        byte[] entryDigest;
        if (reactionInfo.getCommentId() == null) {
            entryDigest = postingSignatureVerifier.verifySignature(
                nodeName, reactionInfo.getPostingId(), reactionInfo.getPostingRevisionId(), carte
            );
        } else {
            entryDigest = commentSignatureVerifier.verifySignature(
                nodeName, reactionInfo.getPostingId(), reactionInfo.getCommentId(), reactionInfo.getCommentRevisionId(),
                carte
            );
        }
        byte[] signingKey = signingKey(reactionInfo.getOwnerName(), reactionInfo.getCreatedAt());
        byte[] fingerprint = ReactionFingerprintBuilder.build(
            reactionInfo.getSignatureVersion(), reactionInfo, entryDigest
        );
        if (!CryptoUtil.verifySignature(fingerprint, reactionInfo.getSignature(), signingKey)) {
            if (reactionInfo.getCreatedAt() >= VERIFICATION_FAILURE_CUTOFF_TIMESTAMP) {
                throw new SignatureVerificationException("Reaction signature is incorrect");
            }
            log.error("Reaction signature is incorrect, but it is created before cutoff");
        }
    }

}
