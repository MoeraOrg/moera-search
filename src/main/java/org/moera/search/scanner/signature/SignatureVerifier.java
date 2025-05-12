package org.moera.search.scanner.signature;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.function.Function;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import org.moera.lib.naming.MoeraNaming;
import org.moera.lib.naming.NodeName;
import org.moera.lib.naming.types.RegisteredNameInfo;
import org.moera.search.config.Config;
import org.moera.search.media.MediaManager;

class SignatureVerifier {

    protected static final long VERIFICATION_FAILURE_CUTOFF_TIMESTAMP =
        LocalDateTime.of(2025, 4, 12, 17, 0, 0, 0).toInstant(ZoneOffset.UTC).getEpochSecond(); // Pesah 5785

    private MoeraNaming naming;

    @Inject
    private Config config;

    @Inject
    private MediaManager mediaManager;

    @PostConstruct
    public void init() {
        naming = new MoeraNaming(config.getNamingServer());
    }

    protected byte[] signingKey(String remoteNodeName, long at) {
        NodeName registeredName = NodeName.parse(remoteNodeName);
        RegisteredNameInfo nameInfo = naming.getPast(registeredName.getName(), registeredName.getGeneration(), at);
        if (nameInfo == null || nameInfo.getSigningKey() == null) {
            throw new SignatureVerificationException("Cannot get signing key for " + remoteNodeName);
        }
        return nameInfo.getSigningKey();
    }

    protected Function<String, byte[]> mediaDigest(String remoteNodeName, String carte) {
        return mediaManager.privateMediaDigestGetter(remoteNodeName, carte);
    }

}
