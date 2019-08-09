package com.radixdlt.ledger.exceptions;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.ledger.LedgerIndex;

import java.util.Objects;

public class LedgerKeyConstraintException extends LedgerException {
	private final ImmutableSet<LedgerIndex> offendingIndices;

	public LedgerKeyConstraintException(ImmutableSet<LedgerIndex> offendingIndices) {
		super(getMessage(offendingIndices));
		this.offendingIndices = offendingIndices;
	}

	public LedgerKeyConstraintException(ImmutableSet<LedgerIndex> offendingIndices, Throwable cause) {
		super(getMessage(offendingIndices), cause);
		this.offendingIndices = offendingIndices;
	}

	private static String getMessage(ImmutableSet<LedgerIndex> offendingIndices) {
		Objects.requireNonNull(offendingIndices, "offendingIndices is required");
		return String.format("Indices violated key constraints: %s", offendingIndices);
	}

	public ImmutableSet<LedgerIndex> getOffendingIndices() {
		return offendingIndices;
	}
}
