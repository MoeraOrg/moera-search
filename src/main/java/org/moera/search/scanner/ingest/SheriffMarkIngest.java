package org.moera.search.scanner.ingest;

import jakarta.inject.Inject;

import org.moera.search.data.Database;
import org.moera.search.data.SheriffMarkRepository;
import org.springframework.stereotype.Component;

@Component
public class SheriffMarkIngest {

    public static final String SHERIFF_USER_LIST_HIDE = "sheriff-hide";

    @Inject
    private Database database;

    @Inject
    private SheriffMarkRepository sheriffMarkRepository;

    @Inject
    private NodeIngest nodeIngest;

    public void ingest(String sheriffName, String nodeName, String postingId, String commentId) {
        if (commentId == null) {
            if (postingId == null) {
                ingestNode(sheriffName, nodeName);
            } else {
                ingestPosting(sheriffName, nodeName, postingId);
            }
        } else {
            ingestComment(sheriffName, nodeName, postingId, commentId);
        }
    }

    private void ingestNode(String sheriffName, String nodeName) {
        nodeIngest.newNode(nodeName);
        database.writeNoResult(() -> {
            sheriffMarkRepository.markNode(sheriffName, nodeName);
            sheriffMarkRepository.markEntriesByOwner(sheriffName, nodeName);
        });
    }

    private void ingestPosting(String sheriffName, String nodeName, String postingId) {
        database.writeNoResult(() -> {
            sheriffMarkRepository.createPostingMark(sheriffName, nodeName, postingId);
            sheriffMarkRepository.markPosting(sheriffName, nodeName, postingId);
        });
    }

    private void ingestComment(String sheriffName, String nodeName, String postingId, String commentId) {
        database.writeNoResult(() -> {
            sheriffMarkRepository.createCommentMark(sheriffName, nodeName, postingId, commentId);
            sheriffMarkRepository.markComment(sheriffName, nodeName, postingId, commentId);
        });
    }

    public void delete(String sheriffName, String nodeName, String postingId, String commentId) {
        if (commentId == null) {
            if (postingId == null) {
                deleteNode(sheriffName, nodeName);
            } else {
                deletePosting(sheriffName, nodeName, postingId);
            }
        } else {
            deleteComment(sheriffName, nodeName, postingId, commentId);
        }
    }

    private void deleteNode(String sheriffName, String nodeName) {
        database.writeNoResult(() -> {
            sheriffMarkRepository.unmarkNode(sheriffName, nodeName);
            sheriffMarkRepository.unmarkPostingsByOwner(sheriffName, nodeName);
            sheriffMarkRepository.unmarkCommentsByOwner(sheriffName, nodeName);
        });
    }

    private void deletePosting(String sheriffName, String nodeName, String postingId) {
        database.writeNoResult(() -> {
            sheriffMarkRepository.deletePostingMark(sheriffName, nodeName, postingId);
            sheriffMarkRepository.unmarkPosting(sheriffName, nodeName, postingId);
        });
    }

    private void deleteComment(String sheriffName, String nodeName, String postingId, String commentId) {
        database.writeNoResult(() -> {
            sheriffMarkRepository.deleteCommentMark(sheriffName, nodeName, postingId, commentId);
            sheriffMarkRepository.unmarkComment(sheriffName, nodeName, postingId, commentId);
        });
    }

}
