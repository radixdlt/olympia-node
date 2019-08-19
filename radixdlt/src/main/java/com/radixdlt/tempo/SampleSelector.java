package com.radixdlt.tempo;

import com.radixdlt.common.EUID;

import java.util.Collection;
import java.util.List;

public interface SampleSelector {
	/**
	 * Select the next edges for an atom from a collection of possible nodes
	 * @param nodes The nodes
	 * @param atom The atom
	 * @return The subset of next edges
	 */
	List<EUID> selectSamples(Collection<EUID> nodes, TempoAtom atom);
}

