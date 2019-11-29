package com.radixdlt.tempo.consensus;

import com.radixdlt.ledger.LedgerEntry;

import java.util.Objects;

public final class ConsensusAction {
	public enum Type {
		COMMIT
	}

	private final Type type;
	private final LedgerEntry preference;

	private ConsensusAction(Type type, LedgerEntry preference) {
		this.type = Objects.requireNonNull(type);
		this.preference = Objects.requireNonNull(preference);
	}

	public Type getType() {
		return type;
	}

	public LedgerEntry getPreference() {
		return preference;
	}

	@Override
	public String toString() {
		return String.format("%s %s", type, preference);
	}

	public static ConsensusAction commit(LedgerEntry preference) {
		return new ConsensusAction(Type.COMMIT, preference);
	}
}
