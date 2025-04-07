package org.moera.search.auth;

import java.net.InetAddress;
import java.time.Instant;
import jakarta.inject.Inject;

import org.moera.lib.crypto.CryptoException;
import org.moera.lib.crypto.CryptoUtil;
import org.moera.lib.crypto.Fingerprint;
import org.moera.lib.crypto.FingerprintException;
import org.moera.lib.crypto.RestoredFingerprint;
import org.moera.lib.node.Fingerprints;
import org.moera.lib.util.LogUtil;
import org.moera.search.api.NamingCache;
import org.moera.search.config.Config;
import org.moera.search.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Component
public class AuthenticationManager {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationManager.class);

    @Inject
    private Config config;

    @Inject
    @Lazy
    private NamingCache namingCache;

    public CarteAuthInfo getCarte(String carteS, InetAddress clientAddress) {
        if (ObjectUtils.isEmpty(carteS)) {
            return null;
        }
        byte[] carte = Util.base64urldecode(carteS);
        if (carte.length == 0) {
            return null;
        }
        Fingerprint fingerprint;
        byte[] signature;
        try {
            RestoredFingerprint rc = CryptoUtil.restoreFingerprint(
                carte, version -> Fingerprints.getSchema("CARTE", version)
            );
            fingerprint = rc.fingerprint();
            signature = new byte[rc.available()];
            System.arraycopy(carte, carte.length - signature.length, signature, 0, signature.length);
        } catch (CryptoException | FingerprintException e) {
            log.info("Carte: unknown fingerprint");
            throw new InvalidCarteException("carte.unknown-fingerprint", e);
        }
        CarteProperties cp = new CarteProperties(fingerprint);
        if (!"CARTE".equals(cp.getObjectType())) {
            log.info("Carte: not a carte fingerprint");
            throw new InvalidCarteException("carte.invalid");
        }
        if (cp.getAddress() != null && clientAddress != null && !cp.getAddress().equals(clientAddress)) {
            log.info("Carte: IP {} differs from client IP {}", cp.getAddress(), clientAddress);
            throw new InvalidCarteException("carte.invalid");
        }
        if (Instant.now().isBefore(cp.getBeginning().toInstant().minusSeconds(120))) {
            log.info("Carte: begins at {} - 2 min", LogUtil.format(cp.getBeginning()));
            throw new InvalidCarteException("carte.not-begun");
        }
        if (Instant.now().isAfter(cp.getDeadline().toInstant().plusSeconds(120))) {
            log.info("Carte: deadline at {} + 2 min", LogUtil.format(cp.getDeadline()));
            throw new InvalidCarteException("carte.expired");
        }
        if (cp.getNodeName() != null && !cp.getNodeName().equals(config.getNodeName())) {
            log.info("Carte: belongs to a wrong node ({})", LogUtil.format(cp.getNodeName()));
            throw new InvalidCarteException("carte.wrong-node");
        }
        byte[] signingKey = namingCache.get(cp.getOwnerName()).getSigningKey();
        if (signingKey == null) {
            log.info("Carte: signing key for node {} is unknown", LogUtil.format(cp.getOwnerName()));
            throw new InvalidCarteException("carte.unknown-signing-key");
        }
        byte[] fingerprintBytes = CryptoUtil.fingerprint(
            fingerprint, Fingerprints.getSchema("CARTE", fingerprint.getVersion())
        );
        if (!CryptoUtil.verifySignature(fingerprintBytes, signature, signingKey)) {
            log.info("Carte: signature verification failed");
            throw new InvalidCarteException("carte.invalid-signature");
        }
        return new CarteAuthInfo(cp);
    }

}
