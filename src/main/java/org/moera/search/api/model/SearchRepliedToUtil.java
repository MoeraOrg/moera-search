package org.moera.search.api.model;

import org.moera.lib.node.types.RepliedTo;
import org.moera.lib.node.types.SearchRepliedTo;

public class SearchRepliedToUtil {

    public static SearchRepliedTo build(RepliedTo repliedTo) {
        var searchRepliedTo = new SearchRepliedTo();
        searchRepliedTo.setId(repliedTo.getId());
        searchRepliedTo.setRevisionId(repliedTo.getRevisionId());
        searchRepliedTo.setName(repliedTo.getName());
        searchRepliedTo.setFullName(repliedTo.getFullName());
        searchRepliedTo.setAvatar(repliedTo.getAvatar());
        searchRepliedTo.setHeading(repliedTo.getHeading());
        return searchRepliedTo;
    }

}
