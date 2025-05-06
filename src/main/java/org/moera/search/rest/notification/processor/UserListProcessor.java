package org.moera.search.rest.notification.processor;

import java.util.Objects;
import jakarta.inject.Inject;

import org.moera.lib.node.types.notifications.NotificationType;
import org.moera.lib.node.types.notifications.UserListItemAddedNotification;
import org.moera.lib.node.types.notifications.UserListItemDeletedNotification;
import org.moera.lib.util.LogUtil;
import org.moera.search.data.Database;
import org.moera.search.data.NodeRepository;
import org.moera.search.rest.notification.NotificationMapping;
import org.moera.search.rest.notification.NotificationProcessor;
import org.moera.search.scanner.UpdateQueue;
import org.moera.search.scanner.ingest.SheriffMarkIngest;
import org.moera.search.scanner.updates.SheriffOrderUpdate;
import org.moera.search.scanner.updates.SheriffScanUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NotificationProcessor
public class UserListProcessor {

    private static final Logger log = LoggerFactory.getLogger(UserListProcessor.class);

    @Inject
    private Database database;

    @Inject
    private NodeRepository nodeRepository;

    @Inject
    private UpdateQueue updateQueue;

    @NotificationMapping(NotificationType.USER_LIST_ITEM_ADDED)
    public void added(UserListItemAddedNotification notification) {
        if (!Objects.equals(notification.getListName(), SheriffMarkIngest.SHERIFF_USER_LIST_HIDE)) {
            return;
        }

        log.info(
            "Sheriff {} ordered to hide all content owned by node {}",
            LogUtil.format(notification.getSenderNodeName()),
            LogUtil.format(notification.getNodeName())
        );
        scanSheriff(notification.getSenderNodeName());
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

        log.info(
            "Sheriff {} ordered to unhide all content owned by node {}",
            LogUtil.format(notification.getSenderNodeName()),
            LogUtil.format(notification.getNodeName())
        );
        scanSheriff(notification.getSenderNodeName());
        updateQueue.offer(new SheriffOrderUpdate(
            true,
            notification.getNodeName(),
            null,
            null,
            null,
            notification.getSenderNodeName()
        ));
    }

    private void scanSheriff(String sheriffName) {
        var sheriffScanned = database.read(() -> nodeRepository.isScanSheriffSucceeded(sheriffName));
        if (!sheriffScanned) {
            log.info("Sheriff {} has not been scanned yet, initiating scan", LogUtil.format(sheriffName));
            updateQueue.offer(new SheriffScanUpdate(sheriffName));
        }
    }

}
