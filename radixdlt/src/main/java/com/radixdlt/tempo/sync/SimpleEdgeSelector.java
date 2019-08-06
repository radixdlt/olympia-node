package com.radixdlt.tempo.sync;

import com.radixdlt.Atom;
import com.radixdlt.common.EUID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Stupid simple placeholder until gossip rings are implemented
 */
public class SimpleEdgeSelector implements EdgeSelector {
	@Override
	public List<EUID> selectEdges(Collection<EUID> nodes, Atom atom) {
		return new ArrayList<>(nodes);
	}
}
