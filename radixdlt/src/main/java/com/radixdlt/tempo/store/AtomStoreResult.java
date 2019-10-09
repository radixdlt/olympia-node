package com.radixdlt.tempo.store;

import java.util.Objects;

public final class AtomStoreResult {
	private static final AtomStoreResult SUCCESS = new AtomStoreResult(null);

	private final AtomConflict conflictInfo;

	private AtomStoreResult(AtomConflict conflictInfo) {
		this.conflictInfo = conflictInfo;
	}

	public boolean isSuccess() {
		return this == SUCCESS;
	}

	public AtomConflict getConflictInfo() {
		return conflictInfo;
	}

	public static AtomStoreResult success() {
		return SUCCESS;
	}

	public static AtomStoreResult conflict(AtomConflict conflict) {
		Objects.requireNonNull(conflict, "conflict is required");
		return new AtomStoreResult(conflict);
	}
}
