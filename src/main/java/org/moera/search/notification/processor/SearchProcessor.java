package org.moera.search.notification.processor;

import java.util.HashSet;
import java.util.Objects;
import jakarta.inject.Inject;

import org.moera.lib.node.types.notifications.NotificationType;
import org.moera.lib.node.types.notifications.SearchContentUpdatedNotification;
import org.moera.lib.util.LogUtil;
import org.moera.search.data.Database;
import org.moera.search.data.NodeRepository;
import org.moera.search.notification.NotificationMapping;
import org.moera.search.notification.NotificationProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NotificationProcessor
public class SearchProcessor {

    private static final Logger log = LoggerFactory.getLogger(SearchProcessor.class);

    @Inject
    private Database database;

    @Inject
    private NodeRepository nodeRepository;

    @NotificationMapping(NotificationType.SEARCH_CONTENT_UPDATED)
    public void searchContentUpdated(SearchContentUpdatedNotification notification) {
        switch (notification.getUpdateType()) {
            case PROFILE:
                log.info("Profile of {} was updated, rescanning", LogUtil.format(notification.getSenderNodeName()));
                database.executeWriteWithoutResult(() -> nodeRepository.rescanName(notification.getSenderNodeName()));
                break;
            case FRIEND:
                log.info(
                    "Node {} added node {} to friends",
                    LogUtil.format(notification.getSenderNodeName()), LogUtil.format(notification.getNodeName())
                );
                database.executeWriteWithoutResult(() ->
                    nodeRepository.addFriendship(notification.getSenderNodeName(), notification.getNodeName())
                );
                break;
            case UNFRIEND:
                log.info(
                    "Node {} removed node {} from friends",
                    LogUtil.format(notification.getSenderNodeName()), LogUtil.format(notification.getNodeName())
                );
                database.executeWriteWithoutResult(() ->
                    nodeRepository.deleteFriendship(notification.getSenderNodeName(), notification.getNodeName())
                );
                break;
            case SUBSCRIBE:
                log.info(
                    "Node {} subscribed to node {}",
                    LogUtil.format(notification.getSenderNodeName()), LogUtil.format(notification.getNodeName())
                );
                database.executeWriteWithoutResult(() ->
                    nodeRepository.addSubscription(
                        notification.getSenderNodeName(), notification.getNodeName(), notification.getFeedName()
                    )
                );
                break;
            case UNSUBSCRIBE:
                log.info(
                    "Node {} unsubscribed from node {}",
                    LogUtil.format(notification.getSenderNodeName()), LogUtil.format(notification.getNodeName())
                );
                database.executeWriteWithoutResult(() ->
                    nodeRepository.deleteSubscription(
                        notification.getSenderNodeName(), notification.getNodeName(), notification.getFeedName()
                    )
                );
                break;
            case BLOCK:
                log.info(
                    "Node {} blocked {} from node {}",
                    LogUtil.format(notification.getSenderNodeName()),
                    LogUtil.format(Objects.toString(notification.getBlockedOperation())),
                    LogUtil.format(notification.getNodeName())
                );
                database.executeWriteWithoutResult(() -> {
                    var blockedOperations = new HashSet<>(
                        nodeRepository.getBlocks(notification.getSenderNodeName(), notification.getNodeName())
                    );
                    blockedOperations.add(notification.getBlockedOperation());
                    nodeRepository.addOrUpdateBlocks(
                        notification.getSenderNodeName(), notification.getNodeName(), blockedOperations
                    );
                });
                break;
            case UNBLOCK:
                log.info(
                    "Node {} unblocked {} from node {}",
                    LogUtil.format(notification.getSenderNodeName()),
                    LogUtil.format(Objects.toString(notification.getBlockedOperation())),
                    LogUtil.format(notification.getNodeName())
                );
                database.executeWriteWithoutResult(() -> {
                    var blockedOperations = new HashSet<>(
                        nodeRepository.getBlocks(notification.getSenderNodeName(), notification.getNodeName())
                    );
                    if (blockedOperations.isEmpty()) {
                        return;
                    }
                    blockedOperations.remove(notification.getBlockedOperation());
                    if (!blockedOperations.isEmpty()) {
                        nodeRepository.addOrUpdateBlocks(
                            notification.getSenderNodeName(), notification.getNodeName(), blockedOperations
                        );
                    } else {
                        nodeRepository.deleteBlocks(notification.getSenderNodeName(), notification.getNodeName());
                    }
                });
                break;
        }
    }

}
