package org.moera.search.data;

import java.awt.Dimension;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.moera.search.media.MimeUtils;
import org.moera.search.util.Util;
import org.neo4j.driver.types.Node;

public class MediaFile {

    private String id;
    private String mimeType;
    private Integer sizeX;
    private Integer sizeY;
    private short orientation = 1;
    private long fileSize;
    private boolean exposed;
    private byte[] digest;
    private long createdAt;

    public MediaFile() {
    }

    public MediaFile(Node node) {
        id = node.get("id").asString(null);
        mimeType = node.get("mimeType").asString(null);
        sizeX = Util.toInteger((Long) node.get("sizeX").asObject());
        sizeY = Util.toInteger((Long) node.get("sizeY").asObject());
        orientation = (short) node.get("orientation").asInt(1);
        fileSize = node.get("fileSize").asLong(0);
        exposed = node.get("exposed").asBoolean(false);
        digest = node.get("digest").asByteArray(null);
        createdAt = node.get("createdAt").asLong(Instant.now().toEpochMilli());
    }

    public Map<String, Object> asMap() {
        var map = new HashMap<String, Object>();
        map.put("id", id);
        map.put("mimeType", mimeType);
        map.put("sizeX", sizeX);
        map.put("sizeY", sizeY);
        map.put("orientation", orientation);
        map.put("fileSize", fileSize);
        map.put("exposed", exposed);
        map.put("digest", digest);
        map.put("createdAt", createdAt);
        return map;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getFileName() {
        return MimeUtils.fileName(id, mimeType);
    }

    public Integer getSizeX() {
        return sizeX;
    }

    public void setSizeX(Integer sizeX) {
        this.sizeX = sizeX;
    }

    public Integer getSizeY() {
        return sizeY;
    }

    public void setSizeY(Integer sizeY) {
        this.sizeY = sizeY;
    }

    public Dimension getDimension() {
        return new Dimension(getSizeX(), getSizeY());
    }

    public void setDimension(Dimension dimension) {
        if (dimension != null) {
            setSizeX(dimension.width);
            setSizeY(dimension.height);
        } else {
            setSizeX(null);
            setSizeY(null);
        }
    }

    public short getOrientation() {
        return orientation;
    }

    public void setOrientation(short orientation) {
        this.orientation = orientation;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public boolean isExposed() {
        return exposed;
    }

    public void setExposed(boolean exposed) {
        this.exposed = exposed;
    }

    public byte[] getDigest() {
        return digest;
    }

    public void setDigest(byte[] digest) {
        this.digest = digest;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

}
