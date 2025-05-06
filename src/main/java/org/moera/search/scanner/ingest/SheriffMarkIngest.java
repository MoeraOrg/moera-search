package org.moera.search.scanner.ingest;

import jakarta.inject.Inject;

import org.moera.search.data.CommentRepository;
import org.moera.search.data.Database;
import org.moera.search.data.NodeRepository;
import org.moera.search.data.PostingRepository;
import org.springframework.stereotype.Component;

@Component
public class SheriffMarkIngest {

    public static final String SHERIFF_USER_LIST_HIDE = "sheriff-hide";

    @Inject
    private Database database;

    @Inject
    private NodeRepository nodeRepository;

    @Inject
    private PostingRepository postingRepository;

    @Inject
    private CommentRepository commentRepository;

    @Inject
    private NodeIngest nodeIngest;

    public void ingest(String sheriffName, String nodeName, String postingId, String commentId, String ownerName) {
        if (ownerName != null) {
            ingestOwner(sheriffName, ownerName);
        } else {
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
    }

    private void ingestOwner(String sheriffName, String ownerName) {
        nodeIngest.newNode(ownerName);
        database.writeNoResult(() -> nodeRepository.sheriffMarkOwner(sheriffName, ownerName));
    }

    private void ingestNode(String sheriffName, String nodeName) {
        database.writeNoResult(() -> nodeRepository.sheriffMark(sheriffName, nodeName));
    }

    private void ingestPosting(String sheriffName, String nodeName, String postingId) {
        database.writeNoResult(() -> postingRepository.sheriffMark(sheriffName, nodeName, postingId));
    }

    private void ingestComment(String sheriffName, String nodeName, String postingId, String commentId) {
        database.writeNoResult(() -> commentRepository.sheriffMark(sheriffName, nodeName, postingId, commentId));
    }

    public void delete(String sheriffName, String nodeName, String postingId, String commentId, String ownerName) {
        if (ownerName != null) {
            deleteOwner(sheriffName, ownerName);
        } else {
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
    }

    private void deleteOwner(String sheriffName, String ownerName) {
        database.writeNoResult(() -> nodeRepository.sheriffUnmarkOwner(sheriffName, ownerName));
    }

    private void deleteNode(String sheriffName, String nodeName) {
        database.writeNoResult(() -> nodeRepository.sheriffUnmark(sheriffName, nodeName));
    }

    private void deletePosting(String sheriffName, String nodeName, String postingId) {
        database.writeNoResult(() -> postingRepository.sheriffUnmark(sheriffName, nodeName, postingId));
    }

    private void deleteComment(String sheriffName, String nodeName, String postingId, String commentId) {
        database.writeNoResult(() -> commentRepository.sheriffUnmark(sheriffName, nodeName, postingId, commentId));
    }

}
