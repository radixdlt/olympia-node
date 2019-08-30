package com.radixdlt.tempo;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.radixdlt.common.EUID;

import java.util.List;
import java.util.Objects;

/**
 * Simple edge selector that selects everything that hasn't been selected yet
 */
public class SimpleEdgeSelector implements EdgeSelector {
	private final PeerSupplier peerSupplier;

	@Inject
	public SimpleEdgeSelector(
		PeerSupplier peerSupplier
	) {
		this.peerSupplier = Objects.requireNonNull(peerSupplier);
	}

	@Override
	public List<EUID> selectEdges(TempoAtom atom) {
		return peerSupplier.getNids().stream()
			.filter(nid -> !atom.getTemporalProof().hasVertexByNID(nid))
			.collect(ImmutableList.toImmutableList());
	}
}
