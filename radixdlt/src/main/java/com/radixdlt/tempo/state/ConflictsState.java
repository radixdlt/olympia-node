package com.radixdlt.tempo.state;

import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.TempoState;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ConflictsState implements TempoState {
	private final Map<EUID, Map<AID, TempoAtom>> conflictingAtoms;
	private final Map<EUID, CompletableFuture<TempoAtom>> winnerFutures;

	public ConflictsState(Map<EUID, Map<AID, TempoAtom>> conflictingAtoms, Map<EUID, CompletableFuture<TempoAtom>> winnerFutures) {
		this.conflictingAtoms = conflictingAtoms;
		this.winnerFutures = winnerFutures;
	}

	public TempoAtom getAtom(EUID tag, AID aid) {
		return conflictingAtoms.get(tag).get(aid);
	}

	public Set<AID> getAids(EUID tag) {
		return conflictingAtoms.get(tag).keySet();
	}

	public Map<EUID, Map<AID, TempoAtom>> getConflictingAtoms() {
		return conflictingAtoms;
	}

	public Map<EUID, CompletableFuture<TempoAtom>> getWinnerFutures() {
		return winnerFutures;
	}
}
