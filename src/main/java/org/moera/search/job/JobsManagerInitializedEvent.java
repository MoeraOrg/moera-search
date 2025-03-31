package org.moera.search.job;

import org.springframework.context.ApplicationEvent;

public class JobsManagerInitializedEvent extends ApplicationEvent {

    public JobsManagerInitializedEvent(Object source) {
        super(source);
    }

}
