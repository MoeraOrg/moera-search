package org.moera.search.model;

import org.moera.lib.node.types.SearchNodeInfo;
import org.moera.search.data.MediaFile;
import org.neo4j.driver.types.Node;

public class SearchNodeInfoUtil {

    public static SearchNodeInfo build(Node node, MediaFile avatar, String avatarShape, float distance) {
        var info = build(node, avatar, avatarShape);
        info.setDistance(distance);
        return info;
    }

    public static SearchNodeInfo build(Node node, MediaFile avatar, String avatarShape, boolean blocked) {
        var info = build(node, avatar, avatarShape);
        info.setDistance(!blocked ? 3 : 4);
        return info;
    }

    private static SearchNodeInfo build(Node node, MediaFile avatar, String avatarShape) {
        var info = new SearchNodeInfo();
        info.setNodeName(node.get("name").asString(null));
        info.setFullName(node.get("fullName").asString(null));
        info.setTitle(node.get("title").asString(null));
        if (avatar != null) {
            info.setAvatar(AvatarImageUtil.build(avatar, avatarShape));
        }
        return info;
    }

}
