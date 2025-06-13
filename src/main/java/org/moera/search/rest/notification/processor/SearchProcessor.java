package org.moera.search.rest.notification.processor;

import java.util.Objects;
import jakarta.inject.Inject;

import org.moera.lib.node.types.notifications.NotificationType;
import org.moera.lib.node.types.notifications.SearchContentUpdatedNotification;
import org.moera.lib.util.LogUtil;
import org.moera.search.api.Feed;
import org.moera.search.data.Database;
import org.moera.search.data.NodeRepository;
import org.moera.search.rest.notification.NotificationMapping;
import org.moera.search.rest.notification.NotificationProcessor;
import org.moera.search.scanner.UpdateQueue;
import org.moera.search.scanner.ingest.NodeIngest;
import org.moera.search.scanner.updates.BlockingUpdate;
import org.moera.search.scanner.updates.CommentAddUpdate;
import org.moera.search.scanner.updates.CommentDeleteUpdate;
import org.moera.search.scanner.updates.CommentHeadingUpdateUpdate;
import org.moera.search.scanner.updates.CommentMediaTextUpdateUpdate;
import org.moera.search.scanner.updates.CommentReactionAddUpdate;
import org.moera.search.scanner.updates.CommentReactionDeleteUpdate;
import org.moera.search.scanner.updates.CommentReactionsDeleteAllUpdate;
import org.moera.search.scanner.updates.CommentUpdateUpdate;
import org.moera.search.scanner.updates.FriendshipUpdate;
import org.moera.search.scanner.updates.PostingAddUpdate;
import org.moera.search.scanner.updates.PostingDeleteUpdate;
import org.moera.search.scanner.updates.PostingHeadingUpdateUpdate;
import org.moera.search.scanner.updates.PostingMediaTextUpdateUpdate;
import org.moera.search.scanner.updates.PostingReactionAddUpdate;
import org.moera.search.scanner.updates.PostingReactionDeleteUpdate;
import org.moera.search.scanner.updates.PostingReactionsDeleteAllUpdate;
import org.moera.search.scanner.updates.PostingUpdateUpdate;
import org.moera.search.scanner.updates.PublicationAddUpdate;
import org.moera.search.scanner.updates.PublicationDeleteUpdate;
import org.moera.search.scanner.updates.SubscriptionUpdate;
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
    private NodeIngest nodeIngest;

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
                    new FriendshipUpdate(false, notification.getSenderNodeName(), details.getNodeName())
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
                    new FriendshipUpdate(true, notification.getSenderNodeName(), details.getNodeName())
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
                    new SubscriptionUpdate(
                        false, notification.getSenderNodeName(), details.getNodeName(), details.getFeedName()
                    )
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
                    new SubscriptionUpdate(
                        true, notification.getSenderNodeName(), details.getNodeName(), details.getFeedName()
                    )
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
                    new BlockingUpdate(
                        false, notification.getSenderNodeName(), details.getNodeName(), details.getBlockedOperation()
                    )
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
                    new BlockingUpdate(
                        true, notification.getSenderNodeName(), details.getNodeName(), details.getBlockedOperation()
                    )
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
                boolean inTimeline = Objects.equals(details.getFeedName(), Feed.TIMELINE);
                boolean inNews = Objects.equals(details.getFeedName(), Feed.NEWS);
                boolean isOriginal = Objects.equals(details.getNodeName(), notification.getSenderNodeName());
                if (isOriginal) {
                    if (inTimeline) {
                        updateQueue.offer(
                            new PostingAddUpdate(notification.getSenderNodeName(), details.getPostingId())
                        );
                    }
                } else {
                    if (inTimeline || inNews) {
                        nodeIngest.newNode(details.getNodeName());
                        updateQueue.offer(
                            new PublicationAddUpdate(
                                details.getNodeName(),
                                details.getPostingId(),
                                notification.getSenderNodeName(),
                                details.getFeedName(),
                                details.getStoryId(),
                                details.getPublishedAt()
                            )
                        );
                    }
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
                updateQueue.offer(new PostingUpdateUpdate(notification.getSenderNodeName(), details.getPostingId()));
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
                if (isOriginal) {
                    updateQueue.offer(
                        new PostingDeleteUpdate(notification.getSenderNodeName(), details.getPostingId())
                    );
                } else {
                    nodeIngest.newNode(details.getNodeName());
                    updateQueue.offer(
                        new PublicationDeleteUpdate(
                            details.getNodeName(), details.getPostingId(), notification.getSenderNodeName()
                        )
                    );
                }
                break;
            }
            case COMMENT_ADD: {
                var details = notification.getCommentUpdate();
                log.info(
                    "Node {} received a comment {} to posting {}",
                    LogUtil.format(notification.getSenderNodeName()),
                    LogUtil.format(details.getCommentId()),
                    LogUtil.format(details.getPostingId())
                );
                updateQueue.offer(
                    new CommentAddUpdate(
                        notification.getSenderNodeName(), details.getPostingId(), details.getCommentId()
                    )
                );
                break;
            }
            case COMMENT_UPDATE: {
                var details = notification.getCommentUpdate();
                log.info(
                    "Node {} received an update for comment {} to posting {}",
                    LogUtil.format(notification.getSenderNodeName()),
                    LogUtil.format(details.getCommentId()),
                    LogUtil.format(details.getPostingId())
                );
                updateQueue.offer(
                    new CommentUpdateUpdate(
                        notification.getSenderNodeName(), details.getPostingId(), details.getCommentId()
                    )
                );
                break;
            }
            case COMMENT_DELETE: {
                var details = notification.getCommentUpdate();
                log.info(
                    "Node {} deleted comment {} to posting {}",
                    LogUtil.format(notification.getSenderNodeName()),
                    LogUtil.format(details.getCommentId()),
                    LogUtil.format(details.getPostingId())
                );
                updateQueue.offer(
                    new CommentDeleteUpdate(
                        notification.getSenderNodeName(), details.getPostingId(), details.getCommentId()
                    )
                );
                break;
            }
            case REACTION_ADD: {
                var details = notification.getReactionUpdate();
                if (details.getCommentId() == null) {
                    log.info(
                        "Node {} received a reaction from node {} to posting {}",
                        LogUtil.format(notification.getSenderNodeName()),
                        LogUtil.format(details.getOwnerName()),
                        LogUtil.format(details.getPostingId())
                    );
                    updateQueue.offer(
                        new PostingReactionAddUpdate(
                            notification.getSenderNodeName(), details.getPostingId(), details.getOwnerName()
                        )
                    );
                } else {
                    log.info(
                        "Node {} received a reaction from node {} to comment {} to posting {}",
                        LogUtil.format(notification.getSenderNodeName()),
                        LogUtil.format(details.getOwnerName()),
                        LogUtil.format(details.getCommentId()),
                        LogUtil.format(details.getPostingId())
                    );
                    updateQueue.offer(
                        new CommentReactionAddUpdate(
                            notification.getSenderNodeName(), details.getPostingId(), details.getCommentId(),
                            details.getOwnerName()
                        )
                    );
                }
                break;
            }
            case REACTION_DELETE: {
                var details = notification.getReactionUpdate();
                if (details.getCommentId() == null) {
                    log.info(
                        "Node {} deleted a reaction from node {} to posting {}",
                        LogUtil.format(notification.getSenderNodeName()),
                        LogUtil.format(details.getOwnerName()),
                        LogUtil.format(details.getPostingId())
                    );
                    updateQueue.offer(
                        new PostingReactionDeleteUpdate(
                            notification.getSenderNodeName(), details.getPostingId(), details.getOwnerName()
                        )
                    );
                } else {
                    log.info(
                        "Node {} deleted a reaction from node {} to comment {} to posting {}",
                        LogUtil.format(notification.getSenderNodeName()),
                        LogUtil.format(details.getOwnerName()),
                        LogUtil.format(details.getCommentId()),
                        LogUtil.format(details.getPostingId())
                    );
                    updateQueue.offer(
                        new CommentReactionDeleteUpdate(
                            notification.getSenderNodeName(), details.getPostingId(), details.getCommentId(),
                            details.getOwnerName()
                        )
                    );
                }
                break;
            }
            case REACTIONS_DELETE_ALL: {
                var details = notification.getReactionUpdate();
                if (details.getCommentId() == null) {
                    log.info(
                        "Node {} deleted all reactions to posting {}",
                        LogUtil.format(notification.getSenderNodeName()),
                        LogUtil.format(details.getPostingId())
                    );
                    updateQueue.offer(
                        new PostingReactionsDeleteAllUpdate(notification.getSenderNodeName(), details.getPostingId())
                    );
                } else {
                    log.info(
                        "Node {} deleted all reactions to comment {} to posting {}",
                        LogUtil.format(notification.getSenderNodeName()),
                        LogUtil.format(details.getCommentId()),
                        LogUtil.format(details.getPostingId())
                    );
                    updateQueue.offer(
                        new CommentReactionsDeleteAllUpdate(
                            notification.getSenderNodeName(), details.getPostingId(), details.getCommentId()
                        )
                    );
                }
                break;
            }
            case POSTING_UPDATE_HEADING: {
                var details = notification.getPostingHeadingUpdate();
                log.info(
                    "Node {} updated heading of posting {}",
                    LogUtil.format(notification.getSenderNodeName()),
                    LogUtil.format(details.getPostingId())
                );
                updateQueue.offer(
                    new PostingHeadingUpdateUpdate(
                        notification.getSenderNodeName(), details.getPostingId(), details.getHeading()
                    )
                );
                break;
            }
            case COMMENT_UPDATE_HEADING: {
                var details = notification.getCommentHeadingUpdate();
                log.info(
                    "Node {} updated heading of comment {} to posting {}",
                    LogUtil.format(notification.getSenderNodeName()),
                    LogUtil.format(details.getCommentId()),
                    LogUtil.format(details.getPostingId())
                );
                updateQueue.offer(
                    new CommentHeadingUpdateUpdate(
                        notification.getSenderNodeName(), details.getPostingId(), details.getCommentId(),
                        details.getHeading()
                    )
                );
                break;
            }
            case POSTING_UPDATE_MEDIA_TEXT: {
                var details = notification.getPostingMediaTextUpdate();
                log.info(
                    "Node {} updated text of media {} attached to posting {}",
                    LogUtil.format(notification.getSenderNodeName()),
                    LogUtil.format(details.getMediaId()),
                    LogUtil.format(details.getPostingId())
                );
                updateQueue.offer(
                    new PostingMediaTextUpdateUpdate(
                        notification.getSenderNodeName(), details.getPostingId(), details.getMediaId(),
                        details.getTextContent()
                    )
                );
                break;
            }
            case COMMENT_UPDATE_MEDIA_TEXT: {
                var details = notification.getCommentMediaTextUpdate();
                log.info(
                    "Node {} updated text of media {} attached to comment {} to posting {}",
                    LogUtil.format(notification.getSenderNodeName()),
                    LogUtil.format(details.getMediaId()),
                    LogUtil.format(details.getCommentId()),
                    LogUtil.format(details.getPostingId())
                );
                updateQueue.offer(
                    new CommentMediaTextUpdateUpdate(
                        notification.getSenderNodeName(), details.getPostingId(), details.getCommentId(),
                        details.getMediaId(), details.getTextContent()
                    )
                );
                break;
            }
        }
    }

}
