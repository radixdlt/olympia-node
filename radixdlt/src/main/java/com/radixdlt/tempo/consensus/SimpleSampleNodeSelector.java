package com.radixdlt.tempo.consensus;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAtom;
import org.radix.network2.addressbook.Peer;

import java.util.List;
import java.util.stream.Stream;

public final class SimpleSampleNodeSelector implements SampleNodeSelector {
	private final EUID self;

	@Inject
	public SimpleSampleNodeSelector(
		@Named("self") EUID self
	) {
		this.self = self;
	}

	@Override
	public List<Peer> selectNodes(Stream<Peer> peers, TempoAtom atom, int limit) {
		return peers
			.filter(Peer::hasSystem)
			.collect(ImmutableList.toImmutableList());
	}
}
