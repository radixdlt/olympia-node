package com.radixdlt.tempo.consensus;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAtom;

import java.util.Collection;
import java.util.List;

public final class SimpleSampleSelector implements SampleSelector {
	private final EUID self;

	public SimpleSampleSelector(EUID self) {
		this.self = self;
	}

	@Override
	public List<EUID> selectSamples(Collection<EUID> nodes, TempoAtom atom) {
		return nodes.stream()
			.filter(nid -> !nid.equals(self))
			.collect(ImmutableList.toImmutableList());
	}
}
