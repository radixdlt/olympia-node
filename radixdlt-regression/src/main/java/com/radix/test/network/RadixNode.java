package com.radix.test.network;

import java.util.Set;

public class RadixNode {

    private final String rootUrl;
    private final int primaryPort;
    private final int secondaryPort;
    private final String containerName;
    private final Set<ServiceType> availableServices;

    /**
     * TODO explain
     */
    public enum ServiceType {
        ARCHIVE,
        ACCOUNT,
        CONSTRUCTION,
        SYSTEM,
        VALIDATION,
        FAUCET,
        DEVELOPER
    }

    public RadixNode(String rootUrl, int primaryPort, int secondaryPort, String containerName, Set<ServiceType> availableServices) {
        this.rootUrl = rootUrl;
        this.primaryPort = primaryPort;
        this.secondaryPort = secondaryPort;
        this.containerName = containerName;
        this.availableServices = availableServices;
    }

    public String getRootUrl() {
        return rootUrl;
    }

    public int getSecondaryPort() {
        return secondaryPort;
    }

    public Set<ServiceType> getAvailableServices() {
        return availableServices;
    }

    public String toString() {
        return String.format("%s:%d:%d, container: %S, services: %s", rootUrl, primaryPort, secondaryPort, containerName,
            availableServices);
    }

}
