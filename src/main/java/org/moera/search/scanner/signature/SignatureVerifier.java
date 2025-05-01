package org.moera.search.scanner.signature;

import java.util.function.Function;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import org.moera.lib.naming.MoeraNaming;
import org.moera.lib.naming.NodeName;
import org.moera.lib.naming.types.RegisteredNameInfo;
import org.moera.search.config.Config;
import org.moera.search.media.MediaManager;

class SignatureVerifier {

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
