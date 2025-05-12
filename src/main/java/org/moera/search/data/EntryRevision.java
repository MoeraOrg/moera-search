package org.moera.search.data;

import java.util.Objects;

import org.moera.lib.node.types.CommentInfo;
import org.moera.lib.node.types.CommentOperations;
import org.moera.lib.node.types.PostingInfo;
import org.moera.lib.node.types.PostingOperations;
import org.moera.lib.node.types.principal.Principal;

public record EntryRevision(String revisionId, String viewPrincipal) {

    public boolean sameRevision(String revisionId, String viewPrincipal) {
        return Objects.equals(this.revisionId, revisionId) && Objects.equals(this.viewPrincipal, viewPrincipal);
    }

    public boolean sameRevision(PostingInfo postingInfo) {
        return sameRevision(
            postingInfo.getRevisionId(),
            PostingOperations.getView(postingInfo.getOperations(), Principal.PUBLIC).getValue()
        );
    }

    public boolean sameRevision(CommentInfo commentInfo) {
        return sameRevision(
            commentInfo.getRevisionId(),
            CommentOperations.getView(commentInfo.getOperations(), Principal.PUBLIC).getValue()
        );
    }

}
