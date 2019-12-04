package com.radixdlt.store.berkeley;

import com.radixdlt.common.AID;
import com.radixdlt.store.SearchCursor;
import com.radixdlt.store.StoreIndex;
import org.bouncycastle.util.Arrays;

import java.util.Objects;

/**
 * A Tempo implementation of a {@link SearchCursor}
 */
public class BerkeleySearchCursor implements SearchCursor {
	private final StoreIndex.LedgerIndexType type;
	private final byte[] primary;
	private final byte[] index;
	private final BerkeleyLedgerEntryStore store;

	BerkeleySearchCursor(BerkeleyLedgerEntryStore store, StoreIndex.LedgerIndexType type, byte[] primary, byte[] index) {
		this.type = type;
		this.primary = Arrays.clone(Objects.requireNonNull(primary));
		this.index = Arrays.clone(Objects.requireNonNull(index));
		this.store = store;
	}

	@Override
	public StoreIndex.LedgerIndexType getType() {
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
	public SearchCursor next() {
		return this.store.getNext(this);
	}

	@Override
	public SearchCursor previous() {
		return this.store.getPrev(this);
	}

	@Override
	public SearchCursor first() {
		return this.store.getFirst(this);
	}

	@Override
	public SearchCursor last() {
		return this.store.getLast(this);
	}
}
