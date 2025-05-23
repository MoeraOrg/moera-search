package org.moera.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("search")
public class Config {

    private boolean dryRun;
    private String nodeName;
    private String nodeFullName;
    private String nodeTitle;
    private String signingKey;
    private String address;
    private DatabaseConfig database = new DatabaseConfig();
    private IndexConfig index = new IndexConfig();
    private String namingServer;
    private PoolsConfig pools = new PoolsConfig();
    private MediaConfig media = new MediaConfig();
    private String sheriffDefault;

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getNodeFullName() {
        return nodeFullName;
    }

    public void setNodeFullName(String nodeFullName) {
        this.nodeFullName = nodeFullName;
    }

    public String getNodeTitle() {
        return nodeTitle;
    }

    public void setNodeTitle(String nodeTitle) {
        this.nodeTitle = nodeTitle;
    }

    public String getSigningKey() {
        return signingKey;
    }

    public void setSigningKey(String signingKey) {
        this.signingKey = signingKey;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public DatabaseConfig getDatabase() {
        return database;
    }

    public void setDatabase(DatabaseConfig database) {
        this.database = database;
    }

    public IndexConfig getIndex() {
        return index;
    }

    public void setIndex(IndexConfig index) {
        this.index = index;
    }

    public String getNamingServer() {
        return namingServer;
    }

    public void setNamingServer(String namingServer) {
        this.namingServer = namingServer;
    }

    public PoolsConfig getPools() {
        return pools;
    }

    public void setPools(PoolsConfig pools) {
        this.pools = pools;
    }

    public MediaConfig getMedia() {
        return media;
    }

    public void setMedia(MediaConfig media) {
        this.media = media;
    }

    public String getSheriffDefault() {
        return sheriffDefault;
    }

    public void setSheriffDefault(String sheriffDefault) {
        this.sheriffDefault = sheriffDefault;
    }

}
