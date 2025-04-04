package org.moera.search.notification.processor;

import jakarta.inject.Inject;

import org.moera.lib.node.types.SearchContentUpdateType;
import org.moera.lib.node.types.notifications.NotificationType;
import org.moera.lib.node.types.notifications.SearchContentUpdatedNotification;
import org.moera.search.data.Database;
import org.moera.search.data.NodeRepository;
import org.moera.search.notification.NotificationMapping;
import org.moera.search.notification.NotificationProcessor;

@NotificationProcessor
public class SearchProcessor {

    @Inject
    private Database database;

    @Inject
    private NodeRepository nodeRepository;

    @NotificationMapping(NotificationType.SEARCH_CONTENT_UPDATED)
    public void searchContentUpdated(SearchContentUpdatedNotification notification) {
        if (notification.getUpdateType() == SearchContentUpdateType.PROFILE) {
            database.executeWriteWithoutResult(() -> nodeRepository.rescanName(notification.getSenderNodeName()));
        }
    }

}
