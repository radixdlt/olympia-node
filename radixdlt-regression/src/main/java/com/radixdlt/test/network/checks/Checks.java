package com.radixdlt.test.network.checks;

import com.google.common.collect.Maps;
import com.radixdlt.test.network.RadixNetworkConfiguration;
import com.radixdlt.test.network.RadixNode;
import com.radixdlt.test.network.client.RadixHttpClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class Checks {

    private final Map<Class<? extends Check>, Check> checks;

    /**
     * Will run checks against the given nodes, using the given config
     */
    public static Checks forNodesAndCheckConfiguration(List<RadixNode> nodes, RadixNetworkConfiguration configuration) {
        return new Checks(nodes, configuration);
    }

    private Checks(List<RadixNode> nodes, RadixNetworkConfiguration configuration) {
        checks = Maps.newHashMap();

        // initialize the checks
        var livenessCheck = new LivenessCheck(nodes, 5, RadixHttpClient.fromRadixNetworkConfiguration(configuration));
        checks.put(LivenessCheck.class, livenessCheck);
    }

    public void runCheck(String name) {
        AtomicBoolean checked = new AtomicBoolean(false);
        checks.forEach((key, value) -> {
            if (key.getSimpleName().toLowerCase().contains(name.toLowerCase())) {
                value.check();
                checked.set(true);
            }
        });
        if (!checked.get()) {
            throw new IllegalArgumentException("Check named '" + name + "' not found");
        }
    }

    public void runCheck(Class<?> type) {
        if (!checks.containsKey(type)) {
            throw new IllegalArgumentException("Check '" + type + "' not found");
        }
    }

}
