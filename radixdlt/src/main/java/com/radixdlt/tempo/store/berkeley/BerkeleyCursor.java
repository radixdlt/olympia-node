package com.radixdlt.tempo.store.berkeley;

import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerIndex;
import org.bouncycastle.util.Arrays;

import java.util.Objects;

/**
 * A Tempo implementation of a {@link LedgerCursor}
 */
public class BerkeleyCursor implements LedgerCursor {
	private final LedgerIndex.LedgerIndexType type;
	private final byte[] primary;
	private final byte[] index;
	private final BerkeleyLedgerEntryStore store;

	BerkeleyCursor(BerkeleyLedgerEntryStore store, LedgerIndex.LedgerIndexType type, byte[] primary, byte[] index) {
		this.type = type;
		this.primary = Arrays.clone(Objects.requireNonNull(primary));
		this.index = Arrays.clone(Objects.requireNonNull(index));
		this.store = store;
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
		return AID.from(this.primary, Long.BYTES + 1);
	}

	@Override
	public LedgerCursor next() {
		return this.store.getNext(this);
	}

	@Override
	public LedgerCursor previous() {
		return this.store.getPrev(this);
	}

	@Override
	public LedgerCursor first() {
		return this.store.getFirst(this);
	}

	@Override
	public LedgerCursor last() {
		return this.store.getLast(this);
	}
}
