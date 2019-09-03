package com.radixdlt.tempo.store.legacy;

import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerIndex;
import org.bouncycastle.util.Arrays;
import org.radix.atoms.AtomStore;
import org.radix.database.exceptions.DatabaseException;
import org.radix.modules.Modules;

import java.io.UncheckedIOException;
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
		try {
			return Modules.get(AtomStore.class).getNext(this);
		} catch (DatabaseException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public LedgerCursor previous() {
		try {
			return Modules.get(AtomStore.class).getPrev(this);
		} catch (DatabaseException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public LedgerCursor first() {
		try {
			return Modules.get(AtomStore.class).getFirst(this);
		} catch (DatabaseException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public LedgerCursor last() {
		try {
			return Modules.get(AtomStore.class).getLast(this);
		} catch (DatabaseException e) {
			throw new UncheckedIOException(e);
		}
	}
}
