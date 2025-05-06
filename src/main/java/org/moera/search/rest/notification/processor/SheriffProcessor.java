package org.moera.search.rest.notification.processor;

import jakarta.inject.Inject;

import org.moera.lib.node.types.notifications.NotificationType;
import org.moera.lib.node.types.notifications.SheriffOrderForCommentAddedNotification;
import org.moera.lib.node.types.notifications.SheriffOrderForCommentDeletedNotification;
import org.moera.lib.node.types.notifications.SheriffOrderForFeedAddedNotification;
import org.moera.lib.node.types.notifications.SheriffOrderForFeedDeletedNotification;
import org.moera.lib.node.types.notifications.SheriffOrderForPostingAddedNotification;
import org.moera.lib.node.types.notifications.SheriffOrderForPostingDeletedNotification;
import org.moera.search.rest.notification.NotificationMapping;
import org.moera.search.rest.notification.NotificationProcessor;
import org.moera.search.scanner.UpdateQueue;
import org.moera.search.scanner.updates.SheriffOrderUpdate;

@NotificationProcessor
public class SheriffProcessor {

    @Inject
    private UpdateQueue updateQueue;

    @NotificationMapping(NotificationType.SHERIFF_ORDER_FOR_FEED_ADDED)
    public void orderForFeedAdded(SheriffOrderForFeedAddedNotification notification) {
        updateQueue.offer(new SheriffOrderUpdate(
            false,
            null,
            notification.getRemoteNodeName(),
            null,
            null,
            notification.getSenderNodeName()
        ));
    }

    @NotificationMapping(NotificationType.SHERIFF_ORDER_FOR_FEED_DELETED)
    public void orderForFeedDeleted(SheriffOrderForFeedDeletedNotification notification) {
        updateQueue.offer(new SheriffOrderUpdate(
            true,
            null,
            notification.getRemoteNodeName(),
            null,
            null,
            notification.getSenderNodeName()
        ));
    }

    @NotificationMapping(NotificationType.SHERIFF_ORDER_FOR_POSTING_ADDED)
    public void orderForPostingAdded(SheriffOrderForPostingAddedNotification notification) {
        updateQueue.offer(new SheriffOrderUpdate(
            false,
            null,
            notification.getRemoteNodeName(),
            notification.getPostingId(),
            null,
            notification.getSenderNodeName()
        ));
    }

    @NotificationMapping(NotificationType.SHERIFF_ORDER_FOR_POSTING_DELETED)
    public void orderForPostingDeleted(SheriffOrderForPostingDeletedNotification notification) {
        updateQueue.offer(new SheriffOrderUpdate(
            true,
            null,
            notification.getRemoteNodeName(),
            notification.getPostingId(),
            null,
            notification.getSenderNodeName()
        ));
    }

    @NotificationMapping(NotificationType.SHERIFF_ORDER_FOR_COMMENT_ADDED)
    public void orderForCommentAdded(SheriffOrderForCommentAddedNotification notification) {
        updateQueue.offer(new SheriffOrderUpdate(
            false,
            null,
            notification.getRemoteNodeName(),
            notification.getPostingId(),
            notification.getCommentId(),
            notification.getSenderNodeName()
        ));
    }

    @NotificationMapping(NotificationType.SHERIFF_ORDER_FOR_COMMENT_DELETED)
    public void orderForCommentDeleted(SheriffOrderForCommentDeletedNotification notification) {
        updateQueue.offer(new SheriffOrderUpdate(
            true,
            null,
            notification.getRemoteNodeName(),
            notification.getPostingId(),
            notification.getCommentId(),
            notification.getSenderNodeName()
        ));
    }

}
