package com.radixdlt.tempo.consensus;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAtom;

import java.util.Collection;
import java.util.List;

public final class SimpleSampleNodeSelector implements SampleNodeSelector {
	private final EUID self;

	@Inject
	public SimpleSampleNodeSelector(
		@Named("self") EUID self
	) {
		this.self = self;
	}

	@Override
	public List<EUID> selectNodes(Collection<EUID> nodes, TempoAtom atom, int limit) {
		return nodes.stream()
			.filter(nid -> !nid.equals(self))
			.collect(ImmutableList.toImmutableList());
	}
}
