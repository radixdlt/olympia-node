package com.radixdlt.tempo.consensus;

import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerEntry;
import com.radixdlt.tempo.TempoException;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class PendingLedgerEntryState {
	private final Map<AID, PendingLedgerEntry> pendingLedgerEntries = new ConcurrentHashMap<>();

	public void put(LedgerEntry ledgerEntry, Set<LedgerIndex> indices) {
		this.pendingLedgerEntries.put(ledgerEntry.getAID(), new PendingLedgerEntry(ledgerEntry, indices));
	}

	public void remove(AID aid) {
		this.pendingLedgerEntries.remove(aid);
	}

	public boolean isPending(AID aid) {
		return pendingLedgerEntries.containsKey(aid);
	}

	public void forEachPending(Consumer<LedgerEntry> consumer) {
		pendingLedgerEntries.forEach(((aid, pendingLedgerEntry) -> consumer.accept(pendingLedgerEntry.getLedgerEntry())));
	}

	public Set<LedgerIndex> getUniqueIndices(AID aid) {
		PendingLedgerEntry pendingLedgerEntry = pendingLedgerEntries.get(aid);
		if (pendingLedgerEntry == null) {
			throw new TempoException("Pending LedgerEntry '" + aid + " does not exist");
		}
		return pendingLedgerEntry.getUniqueIndices();
	}

	private static final class PendingLedgerEntry {
		private final LedgerEntry ledgerEntry;
		private final Set<LedgerIndex> uniqueIndices;

		private PendingLedgerEntry(LedgerEntry ledgerEntry, Set<LedgerIndex> uniqueIndices) {
			this.ledgerEntry = ledgerEntry;
			this.uniqueIndices = uniqueIndices;
		}

		private LedgerEntry getLedgerEntry() {
			return ledgerEntry;
		}

		private Set<LedgerIndex> getUniqueIndices() {
			return uniqueIndices;
		}
	}
}
