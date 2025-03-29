package org.moera.search.data;

import org.springframework.context.ApplicationEvent;

public class DatabaseInitializedEvent extends ApplicationEvent {

    public DatabaseInitializedEvent(Object source) {
        super(source);
    }

}
