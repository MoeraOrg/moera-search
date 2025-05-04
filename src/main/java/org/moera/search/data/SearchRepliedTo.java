package org.moera.search.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.moera.lib.node.types.AvatarImage;
import org.moera.lib.node.types.RepliedTo;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchRepliedTo {

    private String id;
    private String revisionId;
    private String name;
    private String fullName;
    private AvatarImage avatar;
    private String heading;

    public SearchRepliedTo() {
    }

    public SearchRepliedTo(RepliedTo repliedTo) {
        id = repliedTo.getId();
        revisionId = repliedTo.getRevisionId();
        name = repliedTo.getName();
        fullName = repliedTo.getFullName();
        avatar = repliedTo.getAvatar();
        heading = repliedTo.getHeading();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRevisionId() {
        return revisionId;
    }

    public void setRevisionId(String revisionId) {
        this.revisionId = revisionId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public AvatarImage getAvatar() {
        return avatar;
    }

    public void setAvatar(AvatarImage avatar) {
        this.avatar = avatar;
    }

    public String getHeading() {
        return heading;
    }

    public void setHeading(String heading) {
        this.heading = heading;
    }

}
