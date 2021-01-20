package com.radixdlt.integration.distributed.simulation;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.radixdlt.integration.distributed.simulation.invariants.consensus.NodeEvents;
import com.radixdlt.integration.distributed.simulation.invariants.ledger.ConsensusToLedgerCommittedInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.ledger.LedgerInOrderInvariant;

public final class LedgerMonitors {
    public static Module consensusToLedger() {
        return new AbstractModule() {
            @ProvidesIntoMap
            @MonitorKey(Monitor.CONSENSUS_TO_LEDGER_PROCESSED)
            TestInvariant ledgerProcessedInvariant(NodeEvents nodeEvents) {
                return new ConsensusToLedgerCommittedInvariant(nodeEvents);
            }
        };
    }

    public static Module ordered() {
        return new AbstractModule() {
            @ProvidesIntoMap
            @MonitorKey(Monitor.LEDGER_IN_ORDER)
            TestInvariant ledgerInOrderInvariant() {
                return new LedgerInOrderInvariant();
            }
        };
    }

    private LedgerMonitors() {
        throw new IllegalStateException("Cannot instantiate.");
    }
}
