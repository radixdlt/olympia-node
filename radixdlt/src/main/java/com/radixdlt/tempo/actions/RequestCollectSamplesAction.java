package com.radixdlt.tempo.actions;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoAtom;

public class RequestCollectSamplesAction implements TempoAction {
	private final TempoAtom atom;
	private final ImmutableSet<AID> allAids;
	private final EUID tag;

	public RequestCollectSamplesAction(TempoAtom atom, ImmutableSet<AID> allAids, EUID tag) {
		this.atom = atom;
		this.allAids = allAids;
		this.tag = tag;
	}

	public TempoAtom getAtom() {
		return atom;
	}

	public ImmutableSet<AID> getAllAids() {
		return allAids;
	}

	public EUID getTag() {
		return tag;
	}
}
