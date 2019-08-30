package com.radixdlt.tempo;

import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAtom;

import java.util.Collection;
import java.util.List;

/**
 * An edge selector for active synchronisation
 */
public interface EdgeSelector {
	/**
	 * Select the next edges for an atom
	 * @param atom The atom
	 * @return The subset of next edges
	 */
	List<EUID> selectEdges(TempoAtom atom);
}
