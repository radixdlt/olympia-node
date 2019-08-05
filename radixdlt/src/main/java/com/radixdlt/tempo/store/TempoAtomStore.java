package com.radixdlt.tempo.store;

import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.common.AID;
import com.radixdlt.constraintmachine.CMAtom;
import com.radixdlt.engine.RadixEngineUtils;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerSearchMode;
import com.radixdlt.tempo.AtomStore;
import com.radixdlt.tempo.AtomStoreView;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.exceptions.TempoException;
import com.radixdlt.utils.UInt384;
import org.radix.atoms.Atom;
import org.radix.atoms.PreparedAtom;
import org.radix.database.exceptions.DatabaseException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
	public boolean contains(AID aid) {
		try {
			return atomStoreSupplier.get().hasAtom(aid);
		} catch (DatabaseException e) {
			throw new TempoException("Error while querying hasAtom(" + aid + ")", e);
		}
	}

	@Override
	public Optional<TempoAtom> get(AID aid) {
		try {
			// TODO awful conversion from legacy 'Atom'
			return atomStoreSupplier.get().getAtom(aid)
				.map(legacyAtom -> new TempoAtom(
					(ImmutableAtom) legacyAtom,
					legacyAtom.getAID(),
					legacyAtom.getTimestamp(),
					legacyAtom.getShards(),
					legacyAtom.getTemporalProof()
				));
		} catch (DatabaseException e) {
			throw new TempoException("Error while querying getAtom(" + aid + ")", e);
		}
	}

	@Override
	public boolean store(TempoAtom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		// TODO remove awful conversion
		ImmutableAtom content = (ImmutableAtom) atom.getContent();
		Atom legacyAtom = new Atom(
			content.particleGroups().collect(Collectors.toList()),
			content.getSignatures(),
			content.getMetaData()
		);
		legacyAtom.setTemporalProof(atom.getTemporalProof());
		final CMAtom cmAtom;
		try {
			cmAtom = RadixEngineUtils.toCMAtom(legacyAtom);
		} catch (RadixEngineUtils.CMAtomConversionException e) {
			throw new IllegalStateException();
		}

		try {

			return atomStoreSupplier.get().storeAtom(new PreparedAtom(cmAtom, UInt384.ONE)).isCompleted();
		} catch (IOException e) {
			throw new TempoException("Error while storing atom " + atom.getAID(), e);
		}
	}

	@Override
	public boolean delete(AID aid) {
		try {
			return atomStoreSupplier.get().deleteAtom(aid).isCompleted();
		} catch (DatabaseException e) {
			throw new TempoException("Error while deleting " + aid, e);
		}
	}

	@Override
	public boolean replace(Set<AID> aids, TempoAtom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		// TODO remove awful conversion
		ImmutableAtom content = (ImmutableAtom) atom.getContent();
		Atom legacyAtom = new Atom(
			content.particleGroups().collect(Collectors.toList()),
			content.getSignatures(),
			content.getMetaData()
		);
		legacyAtom.setTemporalProof(atom.getTemporalProof());
		final CMAtom cmAtom;
		try {
			cmAtom = RadixEngineUtils.toCMAtom(legacyAtom);
		} catch (RadixEngineUtils.CMAtomConversionException e) {
			throw new IllegalStateException();
		}

		try {
			return atomStoreSupplier.get().replaceAtom(aids, new PreparedAtom(cmAtom, UInt384.ONE)).isCompleted();
		} catch (IOException e) {
			throw new TempoException("Error while storing atom " + atom.getAID(), e);
		}
	}

	@Override
	public LedgerCursor search(LedgerCursor.Type type, LedgerIndex index, LedgerSearchMode mode) {
		try {
			return atomStoreSupplier.get().search(type, index, mode);
		} catch (DatabaseException e) {
			throw new TempoException("Error while searching for " + index, e);
		}
	}

	private class AtomStoreViewAdapter implements AtomStoreView {
		@Override
		public boolean contains(AID aid) {
			return TempoAtomStore.this.contains(aid);
		}

		@Override
		public Optional<TempoAtom> get(AID aid) {
			return TempoAtomStore.this.get(aid);
		}
	}
}
