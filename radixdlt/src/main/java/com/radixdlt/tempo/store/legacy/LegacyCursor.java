package com.radixdlt.tempo.store.legacy;

import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerIndex;
import org.bouncycastle.util.Arrays;
import org.radix.atoms.AtomStore;
import org.radix.modules.Modules;

import java.util.Objects;

public class LegacyCursor implements LedgerCursor {
	private LedgerIndex.LedgerIndexType type;
	private byte[] primary;
	private byte[] index;

	public LegacyCursor(LedgerIndex.LedgerIndexType type, byte[] primary, byte[] index) {
		this.type = type;
		this.primary = Arrays.clone(Objects.requireNonNull(primary));
		this.index = Arrays.clone(Objects.requireNonNull(index));
	}

	@Override
	public LedgerIndex.LedgerIndexType getType() {
		return this.type;
	}

	public byte[] getPrimary() {
		return this.primary;
	}

	public byte[] getIndex() {
		return this.index;
	}

	@Override
	public AID get() {
		return AID.from(this.primary, Long.BYTES);
	}

	@Override
	public LedgerCursor next() {
		return Modules.get(AtomStore.class).getNext(this);
	}

	@Override
	public LedgerCursor previous() {
		return Modules.get(AtomStore.class).getPrev(this);
	}

	@Override
	public LedgerCursor first() {
		return Modules.get(AtomStore.class).getFirst(this);
	}

	@Override
	public LedgerCursor last() {
		return Modules.get(AtomStore.class).getLast(this);
	}
}
