package com.radixdlt.tempo.consensus;

import com.radixdlt.tempo.TempoAtom;
import org.radix.network2.addressbook.Peer;

import java.util.List;
import java.util.stream.Stream;

public interface SampleNodeSelector {
	/**
	 * Select the next edges for an atom from a collection of possible nodes
	 * @param peers The nodes
	 * @param atom The atom
	 * @param limit
	 * @return The subset of next edges
	 */
	List<Peer> selectNodes(Stream<Peer> peers, TempoAtom atom, int limit);
}

