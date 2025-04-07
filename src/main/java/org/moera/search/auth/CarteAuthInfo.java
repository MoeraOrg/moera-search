package org.moera.search.auth;

public class CarteAuthInfo {

    private final String clientName;
    private final long clientScope;
    private final long adminScope;

    public CarteAuthInfo(CarteProperties properties) {
        clientName = properties.getOwnerName();
        clientScope = properties.getClientScope();
        adminScope = properties.getAdminScope();
    }

    public String getClientName() {
        return clientName;
    }

    public long getClientScope() {
        return clientScope;
    }

    public long getAdminScope() {
        return adminScope;
    }

}
