package com.radixdlt.tempo.store;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerEntry;
import com.radixdlt.ledger.LedgerIndex;

import java.util.stream.Stream;

public final class LedgerEntryConflict {
	private final LedgerEntry ledgerEntry;
	private final ImmutableMap<LedgerIndex, LedgerEntry> conflictingLedgerEntries;

	public LedgerEntryConflict(LedgerEntry ledgerEntry, ImmutableMap<LedgerIndex, LedgerEntry> conflictingLedgerEntries) {
		this.ledgerEntry = ledgerEntry;
		this.conflictingLedgerEntries = conflictingLedgerEntries;
	}

	public LedgerEntry getLedgerEntry() {
		return ledgerEntry;
	}

	public ImmutableMap<LedgerIndex, LedgerEntry> getConflictingLedgerEntries() {
		return conflictingLedgerEntries;
	}

	public ImmutableSet<AID> getConflictingAids() {
		return conflictingLedgerEntries.values().stream()
			.map(LedgerEntry::getAID)
			.collect(ImmutableSet.toImmutableSet());
	}

	public ImmutableSet<AID> getAllAids() {
		return Stream.concat(Stream.of(ledgerEntry), conflictingLedgerEntries.values().stream())
			.map(LedgerEntry::getAID)
			.collect(ImmutableSet.toImmutableSet());
	}

	@Override
	public String toString() {
		return "LedgerEntryConflict{" +
			"ledgerEntry=" + ledgerEntry +
			", conflictingLedgerEntries=" + conflictingLedgerEntries +
			'}';
	}
}
