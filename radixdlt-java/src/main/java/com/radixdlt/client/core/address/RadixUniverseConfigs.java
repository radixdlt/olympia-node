package com.radixdlt.client.core.address;

import java.io.InputStream;

public final class RadixUniverseConfigs {

    private RadixUniverseConfigs() { }

    public static RadixUniverseConfig getBetanet() {
        return RadixUniverseConfig.fromInputStream(getConfigFileStream("betanet.json"));
    }

    public static RadixUniverseConfig getWinterfell() {
        return RadixUniverseConfig.fromInputStream(getConfigFileStream("testuniverse.json"));
    }

    public static RadixUniverseConfig getSunstone() {
        return RadixUniverseConfig.fromInputStream(getConfigFileStream("sunstone.json"));
    }

    public static RadixUniverseConfig getHighgarden() {
        return RadixUniverseConfig.fromInputStream(getConfigFileStream("highgarden.json"));
    }

    public static RadixUniverseConfig getAlphanet() {
        return RadixUniverseConfig.fromInputStream(getConfigFileStream("alphanet.json"));
    }

    private static InputStream getConfigFileStream(String name) {
        String source = "/universe/" + name;
        return RadixUniverseConfig.class.getResourceAsStream(source);
    }
}