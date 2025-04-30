package org.moera.search.rest;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.crypto.CryptoUtil;
import org.moera.lib.naming.NodeName;
import org.moera.lib.node.types.NotificationPacket;
import org.moera.lib.node.types.Result;
import org.moera.lib.node.types.notifications.Notification;
import org.moera.lib.node.types.notifications.NotificationType;
import org.moera.lib.node.types.validate.ValidationFailure;
import org.moera.lib.node.types.validate.ValidationUtil;
import org.moera.lib.util.LogUtil;
import org.moera.search.api.NamingCache;
import org.moera.search.auth.IncorrectSignatureException;
import org.moera.search.global.ApiController;
import org.moera.search.global.NoCache;
import org.moera.search.api.fingerprint.NotificationPacketFingerprintBuilder;
import org.moera.search.rest.notification.NotificationRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

@ApiController
@RequestMapping("/moera/api/notifications")
@NoCache
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    @Inject
    private NotificationRouter notificationRouter;

    @Inject
    private NamingCache namingCache;

    @Inject
    private ObjectMapper objectMapper;

    @PostMapping
    public Result post(@RequestBody NotificationPacket packet) throws Throwable {
        log.info(
            "POST /notifications (nodeName = {}, id = {}, type = {})",
            LogUtil.format(packet.getNodeName()), LogUtil.format(packet.getId()), LogUtil.format(packet.getType())
        );

        packet.validate();
        NotificationType type = NotificationType.forValue(packet.getType());
        ValidationUtil.notNull(type, "notification.type.unknown");
        HandlerMethod handler = notificationRouter.getHandler(type);
        if (handler == null) {
            return Result.OK;
        }
        ValidationUtil.assertion(
            Instant.ofEpochSecond(packet.getCreatedAt()).plus(10, ChronoUnit.MINUTES).isAfter(Instant.now()),
            "notification.created-at.too-old"
        );
        if (!verifySignature(packet)) {
            throw new IncorrectSignatureException();
        }

        Notification notification;
        try {
            notification = objectMapper.readValue(packet.getNotification(), type.getStructure());
        } catch (IOException e) {
            throw new ValidationFailure("notification.notification.invalid");
        }

        notification.validate();

        notification.setSenderNodeName(packet.getNodeName());
        notification.setSenderFullName(packet.getFullName());
        notification.setSenderGender(packet.getGender());
        notification.setSenderAvatar(packet.getAvatar());
        try {
            handler.getMethod().invoke(handler.getBean(), notification);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }

        return Result.OK;
    }

    private boolean verifySignature(NotificationPacket packet) {
        byte[] signingKey = fetchSigningKey(packet.getNodeName());
        if (signingKey == null) {
            return false;
        }

        byte[] fingerprint = NotificationPacketFingerprintBuilder.build(packet.getSignatureVersion(), packet);
        return CryptoUtil.verifySignature(fingerprint, packet.getSignature(), signingKey);
    }

    private byte[] fetchSigningKey(String ownerName) {
        var nameInfo = namingCache.get(NodeName.expand(ownerName));
        return nameInfo != null ? nameInfo.getSigningKey() : null;
    }

}
