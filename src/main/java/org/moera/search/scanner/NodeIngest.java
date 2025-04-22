package org.moera.search.scanner;

import jakarta.inject.Inject;

import org.moera.lib.node.types.BlockedOperation;
import org.moera.search.data.Database;
import org.moera.search.data.NodeRepository;
import org.springframework.stereotype.Component;

@Component
public class NodeIngest {

    @Inject
    private Database database;

    @Inject
    private NodeRepository nodeRepository;

    public void newNode(String nodeName) {
        database.writeIgnoreConflict(() -> nodeRepository.createName(nodeName));
    }

    public void friend(String nodeName, String friendName) {
        database.writeNoResult(() -> nodeRepository.addFriendship(nodeName, friendName));
    }

    public void unfriend(String nodeName, String friendName) {
        database.writeNoResult(() -> nodeRepository.deleteFriendship(nodeName, friendName));
    }

    public void subscribed(String nodeName, String subscriptionName, String feedName) {
        database.writeNoResult(() -> nodeRepository.addSubscription(nodeName, subscriptionName, feedName));
    }

    public void unsubscribed(String nodeName, String subscriptionName, String feedName) {
        database.writeNoResult(() -> nodeRepository.deleteSubscription(nodeName, subscriptionName, feedName));
    }

    public void blocks(String nodeName, String blockedName, BlockedOperation operation) {
        database.writeNoResult(() -> {
            nodeRepository.addBlocks(nodeName, blockedName, operation);
            nodeRepository.deleteCloseTo(nodeName, blockedName);
        });
    }

    public void unblocks(String nodeName, String blockedName, BlockedOperation operation) {
        database.writeNoResult(() -> nodeRepository.deleteBlocks(nodeName, blockedName, operation));
    }

}
