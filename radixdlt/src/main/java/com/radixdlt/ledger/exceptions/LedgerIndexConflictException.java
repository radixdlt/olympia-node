package com.radixdlt.ledger.exceptions;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerEntry;
import com.radixdlt.ledger.LedgerIndex;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * An exception thrown when a unique key constraint is violated
 */
public class LedgerIndexConflictException extends LedgerException {
	private final LedgerEntry ledgerEntry;
	private final ImmutableMap<LedgerIndex, LedgerEntry> conflictingLedgerEntries;

	public LedgerIndexConflictException(LedgerEntry ledgerEntry, ImmutableMap<LedgerIndex, LedgerEntry> conflictingLedgerEntries) {
		super(getMessage(ledgerEntry, conflictingLedgerEntries));
		this.ledgerEntry = Objects.requireNonNull(ledgerEntry, "ledgerEntry is required");
		this.conflictingLedgerEntries = conflictingLedgerEntries;
	}

	private static String getMessage(LedgerEntry ledgerEntry, ImmutableMap<LedgerIndex, LedgerEntry> conflictingLedgerEntries) {
		Objects.requireNonNull(conflictingLedgerEntries, "conflictingLedgerEntries is required");
		return String.format("Atom '%s' violated key constraints: %s", ledgerEntry.getAID(), conflictingLedgerEntries);
	}

	public ImmutableMap<LedgerIndex, LedgerEntry> getConflictingLedgerEntries() {
		return conflictingLedgerEntries;
	}

	public LedgerEntry getLedgerEntry() {
		return ledgerEntry;
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
}
