package org.moera.search.scanner.signature;

import jakarta.inject.Inject;

import org.moera.lib.crypto.CryptoUtil;
import org.moera.lib.node.exception.MoeraNodeException;
import org.moera.lib.node.types.PostingInfo;
import org.moera.lib.node.types.PostingRevisionInfo;
import org.moera.search.api.NodeApi;
import org.moera.search.api.fingerprint.PostingFingerprintBuilder;
import org.moera.search.data.CachePostingDigestRepository;
import org.moera.search.data.Database;
import org.springframework.stereotype.Component;

@Component
public class PostingSignatureVerifier extends SignatureVerifier {

    @Inject
    private Database database;

    @Inject
    private CachePostingDigestRepository cachePostingDigestRepository;

    @Inject
    private NodeApi nodeApi;

    public byte[] verifySignature(
        String nodeName,
        String postingId,
        String revisionId,
        String carte
    ) throws MoeraNodeException {
        byte[] cachedDigest = database.read(() ->
            cachePostingDigestRepository.getDigest(nodeName, postingId, revisionId)
        );
        if (cachedDigest != null) {
            return cachedDigest;
        }
        var posting = nodeApi
            .at(nodeName, carte)
            .getPosting(postingId, false);
        if (posting == null) {
            throw new SignatureVerificationException("Posting is not found");
        }
        if (revisionId == null || posting.getRevisionId().equals(revisionId)) {
            if (posting.getSignature() == null) {
                throw new SignatureVerificationException("Posting is not signed");
            }
            return verifySignature(nodeName, posting, carte);
        } else {
            var revision = nodeApi
                .at(nodeName, carte)
                .getPostingRevision(postingId, revisionId);
            if (revision.getSignature() == null) {
                throw new SignatureVerificationException("Posting revision is not signed");
            }
            return verifySignature(nodeName, posting, revision, carte);
        }
    }

    public byte[] verifySignature(
        String nodeName,
        PostingInfo postingInfo,
        String carte
    ) {
        byte[] signingKey = signingKey(postingInfo.getOwnerName(), postingInfo.getEditedAt());
        byte[] fingerprint = PostingFingerprintBuilder.build(
            postingInfo.getSignatureVersion(), postingInfo, null, mediaDigest(nodeName, carte)
        );
        if (!CryptoUtil.verifySignature(fingerprint, postingInfo.getSignature(), signingKey)) {
            throw new SignatureVerificationException("Posting signature is incorrect");
        }
        byte[] digest = CryptoUtil.digest(fingerprint);
        database.writeNoResult(() ->
            cachePostingDigestRepository.storeDigest(nodeName, postingInfo.getId(), postingInfo.getRevisionId(), digest)
        );
        return digest;
    }

    public byte[] verifySignature(
        String nodeName,
        PostingInfo postingInfo,
        PostingRevisionInfo postingRevisionInfo,
        String carte
    ) {
        byte[] signingKey = signingKey(postingInfo.getOwnerName(), postingRevisionInfo.getCreatedAt());
        byte[] fingerprint = PostingFingerprintBuilder.build(
            postingInfo.getSignatureVersion(), postingInfo, postingRevisionInfo, null, mediaDigest(nodeName, carte)
        );
        if (!CryptoUtil.verifySignature(fingerprint, postingRevisionInfo.getSignature(), signingKey)) {
            throw new SignatureVerificationException("Posting signature is incorrect");
        }
        byte[] digest = CryptoUtil.digest(fingerprint);
        database.writeNoResult(() ->
            cachePostingDigestRepository.storeDigest(
                nodeName, postingInfo.getId(), postingRevisionInfo.getId(), digest
            )
        );
        return digest;
    }

}
