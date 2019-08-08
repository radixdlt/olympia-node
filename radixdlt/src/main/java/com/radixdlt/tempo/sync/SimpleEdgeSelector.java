package com.radixdlt.tempo.sync;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAtom;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

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
