package org.moera.search.scanner.signature;

import java.util.HashSet;
import java.util.Set;
import jakarta.inject.Inject;

import org.moera.lib.crypto.CryptoUtil;
import org.moera.lib.node.exception.MoeraNodeException;
import org.moera.lib.node.types.CommentInfo;
import org.moera.lib.node.types.CommentRevisionInfo;
import org.moera.search.api.NodeApi;
import org.moera.search.api.fingerprint.CommentFingerprintBuilder;
import org.moera.search.data.CacheCommentDigestRepository;
import org.moera.search.data.Database;
import org.springframework.stereotype.Component;

@Component
public class CommentSignatureVerifier extends SignatureVerifier {

    @Inject
    private Database database;

    @Inject
    private CacheCommentDigestRepository cacheCommentDigestRepository;

    @Inject
    private PostingSignatureVerifier postingSignatureVerifier;

    @Inject
    private NodeApi nodeApi;

    public byte[] verifySignature(
        String nodeName,
        String postingId,
        String commentId,
        String revisionId,
        String carte
    ) throws MoeraNodeException {
        return verifySignature(nodeName, postingId, commentId, revisionId, carte, new HashSet<>());
    }

    private byte[] verifySignature(
        String nodeName,
        String postingId,
        String commentId,
        String revisionId,
        String carte,
        Set<String> visited
    ) throws MoeraNodeException {
        if (visited.contains(commentId)) {
            throw new SignatureVerificationException("Reply loop in comments");
        }
        visited.add(commentId);

        byte[] cachedDigest = database.read(() ->
            cacheCommentDigestRepository.getDigest(nodeName, postingId, commentId, revisionId)
        );
        if (cachedDigest != null) {
            return cachedDigest;
        }
        var comment = nodeApi
            .at(nodeName, carte)
            .getComment(postingId, commentId, false);
        if (comment == null) {
            throw new SignatureVerificationException("Comment is not found");
        }
        if (revisionId == null || comment.getRevisionId().equals(revisionId)) {
            if (comment.getSignature() == null) {
                throw new SignatureVerificationException("Comment is not signed");
            }
            return verifySignature(nodeName, comment, carte, visited);
        } else {
            var revision = nodeApi
                .at(nodeName, carte)
                .getCommentRevision(postingId, commentId, revisionId);
            if (revision.getSignature() == null) {
                throw new SignatureVerificationException("Comment revision is not signed");
            }
            return verifySignature(nodeName, comment, revision, carte, visited);
        }
    }

    public byte[] verifySignature(
        String nodeName,
        CommentInfo commentInfo,
        String carte
    ) throws MoeraNodeException {
        return verifySignature(nodeName, commentInfo, carte, new HashSet<>());
    }

    private byte[] verifySignature(
        String nodeName,
        CommentInfo commentInfo,
        String carte,
        Set<String> visited
    ) throws MoeraNodeException {
        byte[] postingDigest = postingSignatureVerifier.verifySignature(
            nodeName, commentInfo.getPostingId(), commentInfo.getPostingRevisionId(), carte
        );
        byte[] repliedToDigest = commentInfo.getRepliedTo() != null
            ? verifySignature(
                nodeName,
                commentInfo.getPostingId(),
                commentInfo.getRepliedTo().getId(),
                commentInfo.getRepliedTo().getRevisionId(),
                carte,
                visited
            )
            : null;
        byte[] signingKey = signingKey(commentInfo.getOwnerName(), commentInfo.getEditedAt());
        byte[] fingerprint = CommentFingerprintBuilder.build(
            commentInfo.getSignatureVersion(), commentInfo, mediaDigest(nodeName, carte), postingDigest, repliedToDigest
        );
        if (!CryptoUtil.verifySignature(fingerprint, commentInfo.getSignature(), signingKey)) {
            throw new SignatureVerificationException("Comment signature is incorrect");
        }
        byte[] digest = CryptoUtil.digest(fingerprint);
        database.writeNoResult(() ->
            cacheCommentDigestRepository.storeDigest(
                nodeName, commentInfo.getPostingId(), commentInfo.getId(), commentInfo.getRevisionId(), digest
            )
        );
        return digest;
    }

    public byte[] verifySignature(
        String nodeName,
        CommentInfo commentInfo,
        CommentRevisionInfo commentRevisionInfo,
        String carte
    ) throws MoeraNodeException {
        return verifySignature(nodeName, commentInfo, commentRevisionInfo, carte, new HashSet<>());
    }

    private byte[] verifySignature(
        String nodeName,
        CommentInfo commentInfo,
        CommentRevisionInfo commentRevisionInfo,
        String carte,
        Set<String> visited
    ) throws MoeraNodeException {
        byte[] postingDigest = postingSignatureVerifier.verifySignature(
            nodeName, commentInfo.getPostingId(), commentInfo.getPostingRevisionId(), carte
        );
        byte[] repliedToDigest = commentInfo.getRepliedTo() != null
            ? verifySignature(
                nodeName,
                commentInfo.getPostingId(),
                commentInfo.getRepliedTo().getId(),
                commentInfo.getRepliedTo().getRevisionId(),
                carte,
                visited
            )
            : null;
        byte[] signingKey = signingKey(commentInfo.getOwnerName(), commentInfo.getEditedAt());
        byte[] fingerprint = CommentFingerprintBuilder.build(
            commentInfo.getSignatureVersion(), commentInfo, commentRevisionInfo, mediaDigest(nodeName, carte),
            postingDigest, repliedToDigest
        );
        if (!CryptoUtil.verifySignature(fingerprint, commentRevisionInfo.getSignature(), signingKey)) {
            throw new SignatureVerificationException("Comment signature is incorrect");
        }
        byte[] digest = CryptoUtil.digest(fingerprint);
        database.writeNoResult(() ->
            cacheCommentDigestRepository.storeDigest(
                nodeName, commentInfo.getPostingId(), commentInfo.getId(), commentRevisionInfo.getId(), digest
            )
        );
        return digest;
    }

}
