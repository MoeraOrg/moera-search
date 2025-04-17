package org.moera.search.index;

import org.springframework.context.ApplicationEvent;

public class IndexInitializedEvent extends ApplicationEvent {

    public IndexInitializedEvent(Object source) {
        super(source);
    }

}
