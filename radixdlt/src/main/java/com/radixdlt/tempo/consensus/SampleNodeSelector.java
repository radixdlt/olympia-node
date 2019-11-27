package com.radixdlt.tempo.consensus;

import com.radixdlt.ledger.LedgerEntry;
import org.radix.network2.addressbook.Peer;

import java.util.List;
import java.util.stream.Stream;

public interface SampleNodeSelector {
	/**
	 * Select the next edges for an ledgerEntry from a collection of possible nodes
	 * @param peers The nodes
	 * @param ledgerEntry The ledgerEntry
	 * @param limit
	 * @return The subset of next edges
	 */
	List<Peer> selectNodes(Stream<Peer> peers, LedgerEntry ledgerEntry, int limit);
}

