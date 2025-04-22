package org.moera.search.rest.notification.processor;

import java.util.Objects;
import jakarta.inject.Inject;

import org.moera.lib.node.types.notifications.NotificationType;
import org.moera.lib.node.types.notifications.SearchContentUpdatedNotification;
import org.moera.lib.util.LogUtil;
import org.moera.search.data.Database;
import org.moera.search.data.NodeRepository;
import org.moera.search.rest.notification.NotificationMapping;
import org.moera.search.rest.notification.NotificationProcessor;
import org.moera.search.scanner.JobKeys;
import org.moera.search.scanner.UpdateQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NotificationProcessor
public class SearchProcessor {

    private static final Logger log = LoggerFactory.getLogger(SearchProcessor.class);

    @Inject
    private Database database;

    @Inject
    private NodeRepository nodeRepository;

    @Inject
    private UpdateQueue updateQueue;

    @NotificationMapping(NotificationType.SEARCH_CONTENT_UPDATED)
    public void searchContentUpdated(SearchContentUpdatedNotification notification) {
        switch (notification.getUpdateType()) {
            case PROFILE:
                log.info("Profile of {} was updated, rescanning", LogUtil.format(notification.getSenderNodeName()));
                database.writeNoResult(() -> nodeRepository.rescanName(notification.getSenderNodeName()));
                break;
            case FRIEND: {
                var details = notification.getFriendUpdate();
                log.info(
                    "Node {} added node {} to friends",
                    LogUtil.format(notification.getSenderNodeName()), LogUtil.format(details.getNodeName())
                );
                database.writeIgnoreConflict(() -> nodeRepository.createName(details.getNodeName()));
                updateQueue.offer(
                    notification.getSenderNodeName(),
                    notification.getUpdateType(),
                    details,
                    JobKeys.nodeRelative(notification.getSenderNodeName())
                );
                break;
            }
            case UNFRIEND: {
                var details = notification.getFriendUpdate();
                log.info(
                    "Node {} removed node {} from friends",
                    LogUtil.format(notification.getSenderNodeName()), LogUtil.format(details.getNodeName())
                );
                database.writeIgnoreConflict(() -> nodeRepository.createName(details.getNodeName()));
                updateQueue.offer(
                    notification.getSenderNodeName(),
                    notification.getUpdateType(),
                    details,
                    JobKeys.nodeRelative(notification.getSenderNodeName())
                );
                break;
            }
            case SUBSCRIBE: {
                var details = notification.getSubscriptionUpdate();
                log.info(
                    "Node {} subscribed to node {}",
                    LogUtil.format(notification.getSenderNodeName()), LogUtil.format(details.getNodeName())
                );
                database.writeIgnoreConflict(() -> nodeRepository.createName(details.getNodeName()));
                updateQueue.offer(
                    notification.getSenderNodeName(),
                    notification.getUpdateType(),
                    details,
                    JobKeys.nodeRelative(notification.getSenderNodeName())
                );
                break;
            }
            case UNSUBSCRIBE: {
                var details = notification.getSubscriptionUpdate();
                log.info(
                    "Node {} unsubscribed from node {}",
                    LogUtil.format(notification.getSenderNodeName()), LogUtil.format(details.getNodeName())
                );
                database.writeIgnoreConflict(() -> nodeRepository.createName(details.getNodeName()));
                updateQueue.offer(
                    notification.getSenderNodeName(),
                    notification.getUpdateType(),
                    details,
                    JobKeys.nodeRelative(notification.getSenderNodeName())
                );
                break;
            }
            case BLOCK: {
                var details = notification.getBlockUpdate();
                log.info(
                    "Node {} blocked {} from node {}",
                    LogUtil.format(notification.getSenderNodeName()),
                    LogUtil.format(Objects.toString(details.getBlockedOperation())),
                    LogUtil.format(details.getNodeName())
                );
                database.writeIgnoreConflict(() -> nodeRepository.createName(details.getNodeName()));
                updateQueue.offer(
                    notification.getSenderNodeName(),
                    notification.getUpdateType(),
                    details,
                    JobKeys.nodeRelative(notification.getSenderNodeName())
                );
                break;
            }
            case UNBLOCK: {
                var details = notification.getBlockUpdate();
                log.info(
                    "Node {} unblocked {} from node {}",
                    LogUtil.format(notification.getSenderNodeName()),
                    LogUtil.format(Objects.toString(details.getBlockedOperation())),
                    LogUtil.format(details.getNodeName())
                );
                database.writeIgnoreConflict(() -> nodeRepository.createName(details.getNodeName()));
                updateQueue.offer(
                    notification.getSenderNodeName(),
                    notification.getUpdateType(),
                    details,
                    JobKeys.nodeRelative(notification.getSenderNodeName())
                );
                break;
            }
        }
    }

}
