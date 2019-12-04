package com.radixdlt.store;

import java.util.Objects;

public final class LedgerEntryStoreResult {
	private static final LedgerEntryStoreResult SUCCESS = new LedgerEntryStoreResult(null);

	private final LedgerEntryConflict conflictInfo;

	private LedgerEntryStoreResult(LedgerEntryConflict conflictInfo) {
		this.conflictInfo = conflictInfo;
	}

	public boolean isSuccess() {
		return this == SUCCESS;
	}

	public LedgerEntryConflict getConflictInfo() {
		return conflictInfo;
	}

	public static LedgerEntryStoreResult success() {
		return SUCCESS;
	}

	public static LedgerEntryStoreResult conflict(LedgerEntryConflict conflict) {
		Objects.requireNonNull(conflict, "conflict is required");
		return new LedgerEntryStoreResult(conflict);
	}
}
