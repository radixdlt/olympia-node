package com.radixdlt.tempo.store;

import com.radixdlt.common.AID;
import com.radixdlt.constraintmachine.CMAtom;
import com.radixdlt.engine.RadixEngineUtils;
import com.radixdlt.ledger.DuplicateIndexCreator;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerSearchMode;
import com.radixdlt.ledger.UniqueIndexCreator;
import com.radixdlt.tempo.AtomStore;
import com.radixdlt.tempo.AtomStoreView;
import com.radixdlt.utils.UInt384;
import org.radix.atoms.Atom;
import org.radix.atoms.PreparedAtom;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public class TempoAtomStore implements AtomStore {
	private final Logger logger = Logging.getLogger("Store");
	private final Supplier<org.radix.atoms.AtomStore> atomStoreSupplier;
	private final AtomStoreView view;

	public TempoAtomStore(Supplier<org.radix.atoms.AtomStore> atomStoreSupplier) {
		this.atomStoreSupplier = Objects.requireNonNull(atomStoreSupplier, "atomStoreSupplier is required");
		this.view = new AtomStoreViewAdapter();
	}

	@Override
	public AtomStoreView asReadOnlyView() {
		return view;
	}

	@Override
	public void register(UniqueIndexCreator uniqueIndexCreator) {
		throw new UnsupportedOperationException("Not yet implemented for legacy compatibility");
	}

	@Override
	public void register(DuplicateIndexCreator duplicateIndexCreator) {
		throw new UnsupportedOperationException("Not yet implemented for legacy compatibility");
	}

	@Override
	public boolean contains(AID aid) throws IOException {
		return atomStoreSupplier.get().hasAtom(aid);
	}

	@Override
	public Optional<Atom> get(AID aid) throws IOException {
		return atomStoreSupplier.get().getAtom(aid);
	}

	@Override
	public List<Atom> delete(AID aid) throws IOException {
		return (List<Atom>) atomStoreSupplier.get().deleteAtom(aid).getObject();
	}

	// TODO make this an AtomStore function that we can execute over a Transaction for safety
	@Override
	public List<Atom> replace(AID aid, Atom atom) throws IOException {
		List<Atom> deletedAtoms = (List<Atom>) atomStoreSupplier.get().deleteAtom(aid).getObject();
		// TODO super hack, remove later!
		final CMAtom cmAtom;
		try {
			cmAtom = RadixEngineUtils.toCMAtom(atom);
		} catch (RadixEngineUtils.CMAtomConversionException e) {
			throw new IllegalStateException();
		}
		atomStoreSupplier.get().storeAtom(new PreparedAtom(cmAtom, UInt384.ONE));

		return deletedAtoms;
	}

	@Override
	public boolean store(Atom atom) throws IOException {
		final CMAtom cmAtom;
		try {
			cmAtom = RadixEngineUtils.toCMAtom(atom);
		} catch (RadixEngineUtils.CMAtomConversionException e) {
			throw new IllegalStateException();
		}
		return atomStoreSupplier.get().storeAtom(new PreparedAtom(cmAtom, UInt384.ONE)).isCompleted();
	}

	@Override
	public LedgerCursor search(LedgerCursor.Type type, LedgerIndex index, LedgerSearchMode mode) throws IOException {
		return atomStoreSupplier.get().search(type, index, mode);
	}

	private class AtomStoreViewAdapter implements AtomStoreView {
		@Override
		public boolean contains(AID aid) {
			try {
				return TempoAtomStore.this.contains(aid);
			} catch (IOException e) {
				throw new IllegalStateException("Error while querying contains(" + aid + ")", e);
			}
		}

		@Override
		public Optional<Atom> get(AID aid) {
			try {
				return TempoAtomStore.this.get(aid);
			} catch (IOException e) {
				throw new IllegalStateException("Error while querying get(" + aid + ")", e);
			}
		}
	}
}
