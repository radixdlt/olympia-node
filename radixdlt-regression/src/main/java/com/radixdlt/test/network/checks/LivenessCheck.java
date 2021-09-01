package com.radixdlt.test.network.checks;

import com.radixdlt.test.network.RadixNode;
import com.radixdlt.test.network.client.RadixHttpClient;
import com.radixdlt.test.utils.TestingUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 1) We query every node for its highest QC and we calculate the max (biggest) of them all.
 * 2) We wait for a few seconds (checks.liveness.patienceSeconds)
 * 3) Repeat step #1.
 * 4) If the value from step #3 is larger than that of step #1 then we have liveness. Otherwise we don't.
 */
public class LivenessCheck implements Check {

    private static final Logger logger = LogManager.getLogger();

    private static final Comparator<EpochView> VIEW_COMPARATOR = Comparator.comparingLong(EpochView::getEpoch)
        .thenComparingLong(EpochView::getView);

    private final List<RadixNode> nodes;
    private final int patienceSeconds;
    private final RadixHttpClient client;

    public LivenessCheck(List<RadixNode> nodes, int patienceSeconds, RadixHttpClient client) {
        this.nodes = nodes;
        this.patienceSeconds = patienceSeconds;
        this.client = client;
    }

    @Override
    public boolean check(Object... options) {
        var patienceSeconds = determinePatience(options);
        var highestQC = getMaxHighestQC(nodes);
        TestingUtils.sleep(patienceSeconds);
        var highestQCAfterAWhile = getMaxHighestQC(nodes);

        var comparisonResult = VIEW_COMPARATOR.compare(highestQC, highestQCAfterAWhile);
        logger.trace("First: {}, second: {}, result: {}", highestQC, highestQCAfterAWhile, comparisonResult);
        return comparisonResult == -1;
    }

    private int determinePatience(Object... options) {
        return (options.length == 1) ? Integer.parseInt(options[0].toString()) : patienceSeconds;
    }

    private EpochView getMaxHighestQC(List<RadixNode> nodes) {
        var maybeHighestView = nodes.stream().map(node -> {
            try {
                var metrics = client.getMetrics(node.getRootUrl() + ":" + node.getSecondaryPort());
                logger.trace("Node ({}}) gave: {}", node, new EpochView(metrics.getEpoch(), metrics.getView()));
                return new EpochView(metrics.getEpoch(), metrics.getView());
            } catch (Exception e) {
                logger.trace("Could not get epoch/view for {}: {}", node, e.getMessage());
                return null;
            }
        }).filter(Objects::nonNull).max(VIEW_COMPARATOR);
        return maybeHighestView.orElseThrow();
    }

}
