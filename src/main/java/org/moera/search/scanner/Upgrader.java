package org.moera.search.scanner;

import jakarta.inject.Inject;

import org.moera.search.Workload;
import org.moera.search.data.Database;
import org.moera.search.data.UpdateQueueInitializedEvent;
import org.moera.search.data.UpgradeRepository;
import org.moera.search.global.RequestCounter;
import org.moera.search.scanner.updates.CommentUpdateUpdate;
import org.moera.search.scanner.updates.PostingUpdateUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class Upgrader implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(Upgrader.class);

    @Inject
    private RequestCounter requestCounter;

    @Inject
    private Database database;

    @Inject
    private UpgradeRepository upgradeRepository;

    @Inject
    private UpdateQueue updateQueue;

    @EventListener(UpdateQueueInitializedEvent.class)
    public void scan() {
        var thread = new Thread(this);
        thread.setName("upgrader");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void run() {
        log.info("Upgrader started");
        try {
            try (var ignored = requestCounter.allot()) {
                try (var ignored2 = database.open()) {
                    if (!rescanPostings()) {
                        return;
                    }
                    if (!rescanComments()) {
                        return;
                    }
                }
            }
        } finally {
            log.info("Upgrader finished");
        }
    }

    private boolean rescanPostings() {
        while (true) {
            var postings = database.read(() ->
                upgradeRepository.findPostingsToRescan(Workload.UPGRADER_MAX_STARTED_UPDATES)
            );
            if (postings.isEmpty()) {
                break;
            }
            for (var posting : postings) {
                log.info(
                    "Initiating rescan of posting {} at node {}",
                    posting.postingId(), posting.nodeName()
                );
                updateQueue.offer(new PostingUpdateUpdate(posting.nodeName(), posting.postingId(), true));
                database.writeNoResult(() ->
                    upgradeRepository.deletePostingRescan(posting.nodeName(), posting.postingId())
                );
            }
            try {
                Thread.sleep(Workload.UPGRADER_UPDATE_START_PERIOD);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return true;
    }

    private boolean rescanComments() {
        while (true) {
            var comments = database.read(() ->
                upgradeRepository.findCommentsToRescan(Workload.UPGRADER_MAX_STARTED_UPDATES)
            );
            if (comments.isEmpty()) {
                break;
            }
            for (var comment : comments) {
                log.info(
                    "Initiating rescan of comment {} under posting {} at node {}",
                    comment.commentId(), comment.postingId(), comment.nodeName()
                );
                updateQueue.offer(
                    new CommentUpdateUpdate(comment.nodeName(), comment.postingId(), comment.commentId(), true)
                );
                database.writeNoResult(() ->
                    upgradeRepository.deleteCommentRescan(comment.nodeName(), comment.postingId(), comment.commentId())
                );
            }
            try {
                Thread.sleep(Workload.UPGRADER_UPDATE_START_PERIOD);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return true;
    }

}
