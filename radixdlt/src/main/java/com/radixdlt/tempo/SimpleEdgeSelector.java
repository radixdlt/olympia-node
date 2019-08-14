package com.radixdlt.tempo;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.EUID;

import java.util.Collection;
import java.util.List;

/**
 * Simple edge selector that selects everything that hasn't been selected yet
 */
public class SimpleEdgeSelector implements EdgeSelector {
	@Override
	public List<EUID> selectEdges(Collection<EUID> nodes, TempoAtom atom) {
		return nodes.stream()
			.filter(node -> !atom.getTemporalProof().hasVertexByNID(node))
			.collect(ImmutableList.toImmutableList());
	}
}
