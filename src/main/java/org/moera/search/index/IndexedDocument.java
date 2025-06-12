package org.moera.search.index;

import java.sql.Timestamp;
import java.util.List;

import org.moera.lib.node.types.CommentInfo;
import org.moera.lib.node.types.CommentOperations;
import org.moera.lib.node.types.MediaAttachment;
import org.moera.lib.node.types.PostingInfo;
import org.moera.lib.node.types.PostingOperations;
import org.moera.lib.node.types.body.Body;
import org.moera.lib.node.types.principal.Principal;
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
    private List<String> news;
    private String subject;
    private String subjectRu;
    private String text;
    private String textRu;
    private String mediaText;
    private String mediaTextRu;
    private int imageCount;
    private boolean videoPresent;
    private List<String> hashtags;
    private String viewPrincipal;

    public IndexedDocument() {
    }

    public IndexedDocument(String nodeName, PostingInfo info) {
        this.nodeName = nodeName;
        postingId = info.getId();
        revisionId = info.getRevisionId();
        createdAt = Util.toTimestamp(info.getCreatedAt());
        ownerName = info.getOwnerName();
        analyzeBody(info.getBody(), info.getMedia());
        viewPrincipal = PostingOperations.getView(info.getOperations(), Principal.PUBLIC).getValue();
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
        viewPrincipal = CommentOperations.getView(info.getOperations(), Principal.PUBLIC).getValue();
    }

    private void analyzeBody(Body body, List<MediaAttachment> media) {
        subject = body.getSubject();
        text = getText(body);
        mediaText = getMediaText(media);
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

    private static String getMediaText(List<MediaAttachment> media) {
        if (ObjectUtils.isEmpty(media)) {
            return null;
        }

        var buf = new StringBuilder();
        for (var attachment : media) {
            if (attachment.getMedia() != null && !ObjectUtils.isEmpty(attachment.getMedia().getTextContent())) {
                if (!buf.isEmpty()) {
                    buf.append(' ');
                }
                buf.append(attachment.getMedia().getTextContent());
            }
        }

        return !buf.isEmpty() ? buf.toString() : null;
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

    public List<String> getNews() {
        return news;
    }

    public void setNews(List<String> news) {
        this.news = news;
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

    public String getMediaText() {
        return mediaText;
    }

    public void setMediaText(String mediaText) {
        this.mediaText = mediaText;
    }

    public String getMediaTextRu() {
        return mediaTextRu;
    }

    public void setMediaTextRu(String mediaTextRu) {
        this.mediaTextRu = mediaTextRu;
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

    public String getViewPrincipal() {
        return viewPrincipal;
    }

    public void setViewPrincipal(String viewPrincipal) {
        this.viewPrincipal = viewPrincipal;
    }

}
