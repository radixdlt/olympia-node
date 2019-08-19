package com.radixdlt.tempo.actions;

import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.reactive.TempoAction;
import com.radixdlt.tempo.TempoAtom;

import java.util.Set;

public class OnConflictResolvedAction implements TempoAction {
	private final Set<AID> allConflictingAids;
	private final TempoAtom winner;
	private final EUID tag;

	public OnConflictResolvedAction(TempoAtom winner, Set<AID> allConflictingAids, EUID tag) {
		this.allConflictingAids = allConflictingAids;
		this.winner = winner;
		this.tag = tag;
	}

	public Set<AID> getAllConflictingAids() {
		return allConflictingAids;
	}

	public TempoAtom getWinner() {
		return winner;
	}

	public EUID getTag() {
		return tag;
	}
}
