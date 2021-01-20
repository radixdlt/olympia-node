package com.radixdlt.integration.distributed.simulation;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.radixdlt.integration.distributed.simulation.application.TimestampChecker;
import com.radixdlt.integration.distributed.simulation.invariants.consensus.LivenessInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.consensus.NoTimeoutsInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.consensus.NodeEvents;
import com.radixdlt.integration.distributed.simulation.invariants.consensus.SafetyInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.consensus.VertexRequestRateInvariant;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class ConsensusMonitors {

    public static Module timestampChecker() {
        return timestampChecker(Duration.ofSeconds(1));
    }

    public static Module timestampChecker(Duration maxDelay) {
        return new AbstractModule() {
            @ProvidesIntoMap
            @MonitorKey(Monitor.TIMESTAMP_CHECK)
            public TestInvariant timestampsInvariant() {
                return new TimestampChecker(maxDelay);
            }
        };
    }

    public static Module vertexRequestRate(int permitsPerSecond) {
        return new AbstractModule() {
            @ProvidesIntoMap
            @MonitorKey(Monitor.VERTEX_REQUEST_RATE)
            TestInvariant vertexRequestRateInvariant(NodeEvents nodeEvents) {
                return new VertexRequestRateInvariant(nodeEvents, permitsPerSecond);
            }
        };
    }

    public static Module liveness() {
        return liveness(8 * SimulationNetwork.DEFAULT_LATENCY, TimeUnit.MILLISECONDS);
    }

    public static Module liveness(long duration, TimeUnit timeUnit) {
        return new AbstractModule() {
            @ProvidesIntoMap
            @MonitorKey(Monitor.LIVENESS)
            TestInvariant livenessInvariant(NodeEvents nodeEvents) {
                return new LivenessInvariant(nodeEvents, duration, timeUnit);
            }
        };
    }

    public static Module safety() {
        return new AbstractModule() {
            @ProvidesIntoMap
            @MonitorKey(Monitor.SAFETY)
            TestInvariant safetyInvariant(NodeEvents nodeEvents) {
                return new SafetyInvariant(nodeEvents);
            }
        };
    }

    public static Module noTimeouts() {
        return new AbstractModule() {
            @ProvidesIntoMap
            @MonitorKey(Monitor.NO_TIMEOUTS)
            TestInvariant noTimeoutsInvariant(NodeEvents nodeEvents) {
                return new NoTimeoutsInvariant(nodeEvents);
            }
        };
    }

    private ConsensusMonitors() {
        throw new IllegalStateException("Cannot instantiate.");
    }
}
