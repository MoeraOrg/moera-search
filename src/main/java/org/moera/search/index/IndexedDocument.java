package org.moera.search.index;

import java.sql.Timestamp;
import java.util.List;

import org.moera.lib.node.types.CommentInfo;
import org.moera.lib.node.types.MediaAttachment;
import org.moera.lib.node.types.PostingInfo;
import org.moera.lib.node.types.body.Body;
import org.moera.search.util.BodyUtil;
import org.moera.search.util.Util;
import org.springframework.util.ObjectUtils;

public class IndexedDocument {

    private String nodeName;
    private String postingId;
    private String commentId;
    private String revisionId;
    private Timestamp createdAt;
    private String ownerName;
    private String repliedToName;
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
        analyzeBody(info.getBody(), info.getMedia());
    }

    public IndexedDocument(String nodeName, CommentInfo info) {
        this.nodeName = nodeName;
        postingId = info.getPostingId();
        commentId = info.getId();
        revisionId = info.getRevisionId();
        createdAt = Util.toTimestamp(info.getCreatedAt());
        ownerName = info.getOwnerName();
        if (info.getRepliedTo() != null) {
            repliedToName = info.getRepliedTo().getName();
        }
        analyzeBody(info.getBody(), info.getMedia());
    }

    private void analyzeBody(Body body, List<MediaAttachment> media) {
        subject = body.getSubject();
        text = getText(body);
        var counts = BodyUtil.countBodyMedia(body, media);
        imageCount = counts.imageCount();
        videoPresent = counts.videoPresent();
        hashtags = BodyUtil.extractHashtags(body.getText());
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

    public String getRepliedToName() {
        return repliedToName;
    }

    public void setRepliedToName(String repliedToName) {
        this.repliedToName = repliedToName;
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
