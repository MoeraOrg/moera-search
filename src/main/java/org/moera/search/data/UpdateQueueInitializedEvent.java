package org.moera.search.data;

import org.springframework.context.ApplicationEvent;

public class UpdateQueueInitializedEvent extends ApplicationEvent {

    public UpdateQueueInitializedEvent(Object source) {
        super(source);
    }

}
