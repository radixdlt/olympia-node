package com.radixdlt.test.network.checks;

import com.google.common.collect.Maps;
import com.radixdlt.test.network.RadixNetworkConfiguration;
import com.radixdlt.test.network.RadixNode;
import com.radixdlt.test.network.client.RadixHttpClient;

import java.util.List;
import java.util.Map;

public class Checks {

    private final Map<Class, Check> checks;

    /**
     * Will run checks against the given nodes, using the given config
     */
    public static Checks forNodesAndCheckConfiguration(List<RadixNode> nodes, RadixNetworkConfiguration configuration) {
        return new Checks(nodes, configuration);
    }

    private Checks(List<RadixNode> nodes, RadixNetworkConfiguration configuration) {
        checks = Maps.newHashMap();

        var livenessCheck = new LivenessCheck(nodes, 5, RadixHttpClient.fromRadixNetworkConfiguration(configuration));
        checks.put(LivenessCheck.class, livenessCheck);
    }

    public void runCheck(String name) {
        checks.forEach((key, value) -> {
            if (key.getSimpleName().toLowerCase().contains(name.toLowerCase())) {
                value.check();
            }
        });
    }

    public void runCheck(Class<?> type) {

    }

}
