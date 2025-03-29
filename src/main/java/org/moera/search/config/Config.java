package org.moera.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("search")
public class Config {

    private String nodeName;
    private String nodeFullName;
    private String nodeTitle;
    private DatabaseConfig database = new DatabaseConfig();

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

    public DatabaseConfig getDatabase() {
        return database;
    }

    public void setDatabase(DatabaseConfig database) {
        this.database = database;
    }

}
