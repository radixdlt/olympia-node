package com.radix.test.network;

import java.util.Set;

public class RadixNode {

    private Set<ServiceType> availableNodeServices;

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

    public RadixNode(Set<ServiceType> availableNodeServices) {
        this.availableNodeServices = availableNodeServices;
    }

    public String toString() {
        return availableNodeServices.toString();
    }

}
