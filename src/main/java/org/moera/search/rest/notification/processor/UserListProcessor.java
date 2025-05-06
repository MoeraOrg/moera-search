package org.moera.search.rest.notification.processor;

import java.util.Objects;
import jakarta.inject.Inject;

import org.moera.lib.node.types.notifications.NotificationType;
import org.moera.lib.node.types.notifications.UserListItemAddedNotification;
import org.moera.lib.node.types.notifications.UserListItemDeletedNotification;
import org.moera.search.rest.notification.NotificationMapping;
import org.moera.search.rest.notification.NotificationProcessor;
import org.moera.search.scanner.UpdateQueue;
import org.moera.search.scanner.ingest.SheriffMarkIngest;
import org.moera.search.scanner.updates.SheriffOrderUpdate;

@NotificationProcessor
public class UserListProcessor {

    @Inject
    private UpdateQueue updateQueue;

    @NotificationMapping(NotificationType.USER_LIST_ITEM_ADDED)
    public void added(UserListItemAddedNotification notification) {
        if (!Objects.equals(notification.getListName(), SheriffMarkIngest.SHERIFF_USER_LIST_HIDE)) {
            return;
        }
        updateQueue.offer(new SheriffOrderUpdate(
            false,
            notification.getNodeName(),
            null,
            null,
            null,
            notification.getSenderNodeName()
        ));
    }

    @NotificationMapping(NotificationType.USER_LIST_ITEM_DELETED)
    public void deleted(UserListItemDeletedNotification notification) {
        if (!Objects.equals(notification.getListName(), SheriffMarkIngest.SHERIFF_USER_LIST_HIDE)) {
            return;
        }
        updateQueue.offer(new SheriffOrderUpdate(
            true,
            notification.getNodeName(),
            null,
            null,
            null,
            notification.getSenderNodeName()
        ));
    }

}
