package org.moera.search.scanner.signature;

import java.util.function.Function;

import jakarta.inject.Inject;

import org.moera.lib.crypto.CryptoUtil;
import org.moera.lib.node.types.PostingInfo;
import org.moera.lib.node.types.PostingRevisionInfo;
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

    public void verifySignature(
        String nodeName,
        PostingInfo postingInfo,
        Function<String, byte[]> mediaDigest
    ) {
        byte[] signingKey = signingKey(postingInfo.getOwnerName(), postingInfo.getEditedAt());
        byte[] fingerprint = PostingFingerprintBuilder.build(
            postingInfo.getSignatureVersion(), postingInfo, null, mediaDigest
        );
        database.writeNoResult(() ->
            cachePostingDigestRepository.storeDigest(
                nodeName, postingInfo.getId(), postingInfo.getRevisionId(), CryptoUtil.digest(fingerprint)
            )
        );
        if (!CryptoUtil.verifySignature(fingerprint, postingInfo.getSignature(), signingKey)) {
            throw new SignatureVerificationException("Posting signature is incorrect");
        }
    }

    public void verifySignature(
        String nodeName,
        PostingInfo postingInfo,
        PostingRevisionInfo postingRevisionInfo,
        Function<String, byte[]> mediaDigest
    ) {
        byte [] signingKey = signingKey(postingInfo.getOwnerName(), postingRevisionInfo.getCreatedAt());
        byte[] fingerprint = PostingFingerprintBuilder.build(
            postingInfo.getSignatureVersion(), postingInfo, postingRevisionInfo, null, mediaDigest
        );
        database.writeNoResult(() ->
            cachePostingDigestRepository.storeDigest(
                nodeName, postingInfo.getId(), postingRevisionInfo.getId(), CryptoUtil.digest(fingerprint)
            )
        );
        if (!CryptoUtil.verifySignature(fingerprint, postingRevisionInfo.getSignature(), signingKey)) {
            throw new SignatureVerificationException("Posting signature is incorrect");
        }
    }

}
