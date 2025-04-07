package org.moera.search.auth;

import java.util.Objects;

import org.moera.lib.node.types.Scope;
import org.springframework.stereotype.Component;

@Component
public class RequestContext {

    private final ThreadLocal<Long> clientScope = new ThreadLocal<>();
    private final ThreadLocal<String> clientName = new ThreadLocal<>();

    public long getClientScope() {
        return clientScope.get() != null ? clientScope.get() : Scope.NONE.getMask();
    }

    public void setClientScope(long clientScope) {
        this.clientScope.set(clientScope);
    }

    public boolean hasClientScope(Scope scope) {
        return scope.included(getClientScope());
    }

    public String getClientName(Scope scope) {
        return hasClientScope(scope) ? clientName.get() : null;
    }

    public void setClientName(String clientName) {
        this.clientName.set(clientName);
    }

    public boolean isClient(String name, Scope scope) {
        return Objects.equals(getClientName(scope), name);
    }

}
