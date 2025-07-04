package org.moera.search.config;

public class MediaConfig {

    private String path;
    private String serve = "stream"; // stream, sendfile, accel
    private String accelPrefix = "/";
    private boolean directServe;
    private int avatarMaxSize = 102400;
    private int verifyMaxSize = 10485760;
    private int previewMaxSize = 10485760;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getServe() {
        return serve;
    }

    public void setServe(String serve) {
        this.serve = serve;
    }

    public String getAccelPrefix() {
        return accelPrefix;
    }

    public void setAccelPrefix(String accelPrefix) {
        this.accelPrefix = accelPrefix;
    }

    public boolean isDirectServe() {
        return directServe;
    }

    public void setDirectServe(boolean directServe) {
        this.directServe = directServe;
    }

    public int getAvatarMaxSize() {
        return avatarMaxSize;
    }

    public void setAvatarMaxSize(int avatarMaxSize) {
        this.avatarMaxSize = avatarMaxSize;
    }

    public int getVerifyMaxSize() {
        return verifyMaxSize;
    }

    public void setVerifyMaxSize(int verifyMaxSize) {
        this.verifyMaxSize = verifyMaxSize;
    }

    public int getPreviewMaxSize() {
        return previewMaxSize;
    }

    public void setPreviewMaxSize(int previewMaxSize) {
        this.previewMaxSize = previewMaxSize;
    }

}
