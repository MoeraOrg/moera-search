package org.moera.search.index;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.moera.lib.node.types.CommentInfo;
import org.moera.lib.node.types.PostingInfo;
import org.moera.lib.node.types.body.Body;
import org.moera.search.util.Util;
import org.springframework.util.ObjectUtils;

public class IndexedDocument {

    private static final Pattern VIDEO_TAGS = Pattern.compile("(?i)<(?:object|video|iframe)");
    private static final Pattern HASHTAGS = Pattern.compile("(?U)(?:^|[\\s(\\[{])(#\\w+)\\b");

    private String nodeName;
    private String postingId;
    private String commentId;
    private String revisionId;
    private Timestamp createdAt;
    private String ownerName;
    private List<String> publishers;
    private String subject;
    private String subjectRu;
    private String text;
    private String textRu;
    private int imageCount;
    private boolean videoPresent;
    private List<String> hashtags;

    public IndexedDocument() {
    }

    public IndexedDocument(String nodeName, PostingInfo info) {
        this.nodeName = nodeName;
        postingId = info.getId();
        revisionId = info.getRevisionId();
        createdAt = Util.toTimestamp(info.getCreatedAt());
        ownerName = info.getOwnerName();
        Body body = info.getBody();
        subject = body.getSubject();
        text = getText(body);
        if (info.getMedia() != null) {
            imageCount = info.getMedia().size();
        }
        if (VIDEO_TAGS.matcher(text).find()) {
            videoPresent = true;
        }
        hashtags = extractHashtags(body.getText());
    }

    public IndexedDocument(String nodeName, CommentInfo info) {
        this.nodeName = nodeName;
        postingId = info.getPostingId();
        commentId = info.getId();
        revisionId = info.getRevisionId();
        createdAt = Util.toTimestamp(info.getCreatedAt());
        ownerName = info.getOwnerName();
        Body body = info.getBody();
        subject = body.getSubject();
        text = getText(body);
        if (info.getMedia() != null) {
            imageCount = info.getMedia().size();
        }
        if (VIDEO_TAGS.matcher(text).find()) {
            videoPresent = true;
        }
    }

    private static String getText(Body body) {
        var buf = new StringBuilder();
        if (body.getText() != null) {
            buf.append(body.getText());
        }
        if (body.getLinkPreviews() != null) {
            for (var linkPreview : body.getLinkPreviews()) {
                if (!ObjectUtils.isEmpty(linkPreview.getTitle())) {
                    buf.append(' ');
                    buf.append(linkPreview.getTitle());
                }
                if (!ObjectUtils.isEmpty(linkPreview.getDescription())) {
                    buf.append(' ');
                    buf.append(linkPreview.getDescription());
                }
            }
        }
        return buf.toString();
    }

    private static List<String> extractHashtags(String text) {
        if (ObjectUtils.isEmpty(text)) {
            return Collections.emptyList();
        }
        var hashtags = new ArrayList<String>();
        var m = HASHTAGS.matcher(text);
        while (m.find()) {
            hashtags.add(m.group(1));
        }
        return hashtags;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getPostingId() {
        return postingId;
    }

    public void setPostingId(String postingId) {
        this.postingId = postingId;
    }

    public String getCommentId() {
        return commentId;
    }

    public void setCommentId(String commentId) {
        this.commentId = commentId;
    }

    public String getRevisionId() {
        return revisionId;
    }

    public void setRevisionId(String revisionId) {
        this.revisionId = revisionId;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public List<String> getPublishers() {
        return publishers;
    }

    public void setPublishers(List<String> publishers) {
        this.publishers = publishers;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getSubjectRu() {
        return subjectRu;
    }

    public void setSubjectRu(String subjectRu) {
        this.subjectRu = subjectRu;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getTextRu() {
        return textRu;
    }

    public void setTextRu(String textRu) {
        this.textRu = textRu;
    }

    public int getImageCount() {
        return imageCount;
    }

    public void setImageCount(int imageCount) {
        this.imageCount = imageCount;
    }

    public boolean isVideoPresent() {
        return videoPresent;
    }

    public void setVideoPresent(boolean videoPresent) {
        this.videoPresent = videoPresent;
    }

    public List<String> getHashtags() {
        return hashtags;
    }

    public void setHashtags(List<String> hashtags) {
        this.hashtags = hashtags;
    }

}
