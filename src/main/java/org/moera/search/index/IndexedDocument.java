package org.moera.search.index;

import java.sql.Timestamp;
import java.util.List;

import org.moera.lib.node.types.PostingInfo;
import org.moera.lib.node.types.body.Body;
import org.moera.search.util.Util;

public class IndexedDocument {

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
        text = body.getText();
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

}
