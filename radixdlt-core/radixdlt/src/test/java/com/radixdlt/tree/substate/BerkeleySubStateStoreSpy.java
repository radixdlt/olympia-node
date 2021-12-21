package com.radixdlt.tree.substate;

import com.radixdlt.atom.SubstateId;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.sleepycat.je.Transaction;

import java.util.*;
import java.util.function.Function;

public class BerkeleySubStateStoreSpy extends BerkeleySubStateStore {
    public Map<SubstateId, REStateUpdate> reStateUpdateList = new LinkedHashMap<>();

    @Override
    public void process(Transaction dbTxn, REProcessedTxn txn, long stateVersion, Function<SystemMapKey, Optional<RawSubstateBytes>> mapper) {
        super.process(dbTxn, txn, stateVersion, mapper);
        for (REStateUpdate su : txn.stateUpdates().toList()) {
            this.reStateUpdateList.put(su.getId(), su);
        };
    }

    public List<REStateUpdate> getREStateUpdateList() {
        return this.reStateUpdateList.values().stream().toList();
    }
}
