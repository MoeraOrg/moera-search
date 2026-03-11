package org.moera.search.job;

import tools.jackson.databind.ObjectMapper;

public abstract class StatelessJob<P> extends Job<P, Object> {

    @Override
    protected void setState(String state, ObjectMapper objectMapper) {
        this.state = null;
    }

}
