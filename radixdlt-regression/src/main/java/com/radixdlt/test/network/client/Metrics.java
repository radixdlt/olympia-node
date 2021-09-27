package com.radixdlt.test.network.client;

import com.google.common.collect.Maps;

import java.util.Map;

/**
 * holds the response from /metrics
 */
public class Metrics {

    private final Map<String, Double> metrics;

    public Metrics(String text) {
        metrics = Maps.newHashMap();
        for (var line : text.split("\\R")) {
            if (line.startsWith("#")) {
                continue;
            }
            var key = line.split("\\s+")[0];
            var value = line.split("\\s+")[1];
            metrics.put(key, Double.parseDouble(value));
        }
    }

    public int getEpoch() {
        return metrics.get("info_epochmanager_currentview_epoch").intValue();
    }

    public int getView() {
        return metrics.get("info_epochmanager_currentview_view").intValue();
    }

}
