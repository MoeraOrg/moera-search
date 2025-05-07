package org.moera.search.data;

import org.neo4j.driver.types.Node;

public class Favor {

    private float value;
    private int decayHours;
    private long createdAt;

    public Favor() {
    }

    public Favor(Node node) {
        value = node.get("value").asFloat(0);
        decayHours = node.get("decayHours").asInt(0);
        createdAt = node.get("createdAt").asLong(createdAt);
    }

    public float getValue() {
        return value;
    }

    public void setValue(float value) {
        this.value = value;
    }

    public int getDecayHours() {
        return decayHours;
    }

    public void setDecayHours(int decayHours) {
        this.decayHours = decayHours;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

}
