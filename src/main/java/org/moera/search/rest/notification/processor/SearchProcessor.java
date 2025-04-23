package org.moera.search.rest.notification.processor;

import java.util.Objects;
import jakarta.inject.Inject;

import org.moera.lib.node.types.notifications.NotificationType;
import org.moera.lib.node.types.notifications.SearchContentUpdatedNotification;
import org.moera.lib.util.LogUtil;
import org.moera.search.data.Database;
import org.moera.search.data.NodeRepository;
import org.moera.search.data.PostingRepository;
import org.moera.search.rest.notification.NotificationMapping;
import org.moera.search.rest.notification.NotificationProcessor;
import org.moera.search.scanner.JobKeys;
import org.moera.search.scanner.NodeIngest;
import org.moera.search.scanner.PostingIngest;
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
    private PostingRepository postingRepository;

    @Inject
    private NodeIngest nodeIngest;

    @Inject
    private PostingIngest postingIngest;

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
                nodeIngest.newNode(details.getNodeName());
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
                nodeIngest.newNode(details.getNodeName());
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
                nodeIngest.newNode(details.getNodeName());
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
                nodeIngest.newNode(details.getNodeName());
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
                    "Node {} blocked {} the node {}",
                    LogUtil.format(notification.getSenderNodeName()),
                    LogUtil.format(Objects.toString(details.getBlockedOperation())),
                    LogUtil.format(details.getNodeName())
                );
                nodeIngest.newNode(details.getNodeName());
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
                    "Node {} unblocked {} the node {}",
                    LogUtil.format(notification.getSenderNodeName()),
                    LogUtil.format(Objects.toString(details.getBlockedOperation())),
                    LogUtil.format(details.getNodeName())
                );
                nodeIngest.newNode(details.getNodeName());
                updateQueue.offer(
                    notification.getSenderNodeName(),
                    notification.getUpdateType(),
                    details,
                    JobKeys.nodeRelative(notification.getSenderNodeName())
                );
                break;
            }
            case POSTING_ADD: {
                var details = notification.getPostingUpdate();
                log.info(
                    "Node {} published posting {} from node {} in feed {}",
                    LogUtil.format(notification.getSenderNodeName()),
                    LogUtil.format(details.getPostingId()),
                    LogUtil.format(details.getNodeName()),
                    LogUtil.format(details.getFeedName())
                );
                if (!Objects.equals(details.getFeedName(), "timeline")) {
                    break;
                }
                boolean isOriginal = Objects.equals(details.getNodeName(), notification.getSenderNodeName());
                if (!isOriginal) {
                    nodeIngest.newNode(details.getNodeName());
                }
                boolean isScanned = postingIngest.newPosting(details.getNodeName(), details.getPostingId());
                if (isScanned || !isOriginal) {
                    updateQueue.offer(
                        notification.getSenderNodeName(),
                        notification.getUpdateType(),
                        details,
                        JobKeys.posting(details.getNodeName(), details.getPostingId())
                    );
                }
                break;
            }
            case POSTING_UPDATE: {
                var details = notification.getPostingUpdate();
                log.info(
                    "Node {} updated posting {} from node {}",
                    LogUtil.format(notification.getSenderNodeName()),
                    LogUtil.format(details.getPostingId()),
                    LogUtil.format(details.getNodeName())
                );
                boolean isOriginal = Objects.equals(details.getNodeName(), notification.getSenderNodeName());
                if (!isOriginal) {
                    nodeIngest.newNode(details.getNodeName());
                    break; // other changes from non-original nodes are irrelevant
                }
                boolean isScanned = postingIngest.newPosting(details.getNodeName(), details.getPostingId());
                if (isScanned) {
                    updateQueue.offer(
                        notification.getSenderNodeName(),
                        notification.getUpdateType(),
                        details,
                        JobKeys.posting(details.getNodeName(), details.getPostingId())
                    );
                }
                break;
            }
            case POSTING_DELETE: {
                var details = notification.getPostingUpdate();
                log.info(
                    "Node {} deleted posting {} from node {}",
                    LogUtil.format(notification.getSenderNodeName()),
                    LogUtil.format(details.getPostingId()),
                    LogUtil.format(details.getNodeName())
                );
                boolean isOriginal = Objects.equals(details.getNodeName(), notification.getSenderNodeName());
                if (!isOriginal) {
                    nodeIngest.newNode(details.getNodeName());
                }
                boolean exists = database.read(() ->
                    postingRepository.exists(details.getNodeName(), details.getPostingId())
                );
                if (exists) {
                    updateQueue.offer(
                        notification.getSenderNodeName(),
                        notification.getUpdateType(),
                        details,
                        JobKeys.posting(details.getNodeName(), details.getPostingId())
                    );
                }
                break;
            }
        }
    }

}
