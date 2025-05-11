package org.moera.search.rest.notification.processor;

import java.util.Objects;
import jakarta.inject.Inject;

import org.moera.lib.node.types.notifications.NotificationType;
import org.moera.lib.node.types.notifications.SheriffOrderForCommentAddedNotification;
import org.moera.lib.node.types.notifications.SheriffOrderForCommentDeletedNotification;
import org.moera.lib.node.types.notifications.SheriffOrderForFeedAddedNotification;
import org.moera.lib.node.types.notifications.SheriffOrderForFeedDeletedNotification;
import org.moera.lib.node.types.notifications.SheriffOrderForPostingAddedNotification;
import org.moera.lib.node.types.notifications.SheriffOrderForPostingDeletedNotification;
import org.moera.lib.util.LogUtil;
import org.moera.search.api.Feed;
import org.moera.search.data.Database;
import org.moera.search.data.NodeRepository;
import org.moera.search.rest.notification.NotificationMapping;
import org.moera.search.rest.notification.NotificationProcessor;
import org.moera.search.scanner.UpdateQueue;
import org.moera.search.scanner.updates.SheriffOrderUpdate;
import org.moera.search.scanner.updates.SheriffScanUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NotificationProcessor
public class SheriffProcessor {

    private static final Logger log = LoggerFactory.getLogger(SearchProcessor.class);

    @Inject
    private Database database;

    @Inject
    private NodeRepository nodeRepository;

    @Inject
    private UpdateQueue updateQueue;

    @NotificationMapping(NotificationType.SHERIFF_ORDER_FOR_FEED_ADDED)
    public void orderForFeedAdded(SheriffOrderForFeedAddedNotification notification) {
        if (!Objects.equals(notification.getRemoteFeedName(), Feed.TIMELINE)) {
            return;
        }

        log.info(
            "Sheriff {} ordered to hide the timeline of node {}",
            LogUtil.format(notification.getSenderNodeName()), LogUtil.format(notification.getRemoteNodeName())
        );
        scanSheriff(notification.getSenderNodeName());
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
        if (!Objects.equals(notification.getRemoteFeedName(), Feed.TIMELINE)) {
            return;
        }

        log.info(
            "Sheriff {} ordered to unhide the timeline of node {}",
            LogUtil.format(notification.getSenderNodeName()), LogUtil.format(notification.getRemoteNodeName())
        );
        scanSheriff(notification.getSenderNodeName());
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
        log.info(
            "Sheriff {} ordered to hide posting {} at node {}",
            LogUtil.format(notification.getSenderNodeName()),
            LogUtil.format(notification.getPostingId()),
            LogUtil.format(notification.getRemoteNodeName())
        );
        scanSheriff(notification.getSenderNodeName());
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
        log.info(
            "Sheriff {} ordered to unhide posting {} at node {}",
            LogUtil.format(notification.getSenderNodeName()),
            LogUtil.format(notification.getPostingId()),
            LogUtil.format(notification.getRemoteNodeName())
        );
        scanSheriff(notification.getSenderNodeName());
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
        log.info(
            "Sheriff {} ordered to hide comment {} under posting {} at node {}",
            LogUtil.format(notification.getSenderNodeName()),
            LogUtil.format(notification.getCommentId()),
            LogUtil.format(notification.getPostingId()),
            LogUtil.format(notification.getRemoteNodeName())
        );
        scanSheriff(notification.getSenderNodeName());
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
        log.info(
            "Sheriff {} ordered to unhide comment {} under posting {} at node {}",
            LogUtil.format(notification.getSenderNodeName()),
            LogUtil.format(notification.getCommentId()),
            LogUtil.format(notification.getPostingId()),
            LogUtil.format(notification.getRemoteNodeName())
        );
        scanSheriff(notification.getSenderNodeName());
        updateQueue.offer(new SheriffOrderUpdate(
            true,
            null,
            notification.getRemoteNodeName(),
            notification.getPostingId(),
            notification.getCommentId(),
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
