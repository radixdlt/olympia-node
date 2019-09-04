package com.radixdlt.tempo.store.legacy;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerSearchMode;
import com.radixdlt.ledger.exceptions.LedgerException;
import com.radixdlt.middleware.RadixEngineUtils;
import com.radixdlt.middleware.SimpleRadixEngineAtom;
import com.radixdlt.tempo.store.AtomStoreResult;
import com.radixdlt.tempo.store.TempoAtomStatus;
import com.radixdlt.tempo.store.TempoAtomStore;
import com.radixdlt.tempo.LegacyUtils;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.TempoException;
import com.radixdlt.utils.UInt384;
import org.radix.atoms.Atom;
import org.radix.atoms.AtomDiscoveryRequest;
import org.radix.atoms.PreparedAtom;
import org.radix.atoms.sync.AtomSyncStore;
import org.radix.database.exceptions.DatabaseException;
import org.radix.discovery.DiscoveryCursor;
import org.radix.discovery.DiscoveryException;
import org.radix.discovery.DiscoveryRequest;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.universe.system.LocalSystem;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public class LegacyAtomStoreAdapter implements TempoAtomStore {
	private final Logger logger = Logging.getLogger("store.atoms");
	private final Supplier<org.radix.atoms.AtomStore> atomStoreSupplier;
	private final Supplier<AtomSyncStore> atomSyncStoreSupplier;

	public LegacyAtomStoreAdapter(Supplier<org.radix.atoms.AtomStore> atomStoreSupplier, Supplier<AtomSyncStore> atomSyncStoreSupplier) {
		this.atomStoreSupplier = Objects.requireNonNull(atomStoreSupplier, "atomStoreSupplier is required");
		this.atomSyncStoreSupplier = Objects.requireNonNull(atomSyncStoreSupplier, "atomSyncStoreSupplier is required");
	}

	@Override
	public Set<AID> getPending() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public void commit(AID aid, long logicalClock) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public TempoAtomStatus getStatus(AID aid) {
		throw new UnsupportedOperationException("Not implemented");
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
	public Set<LedgerIndex> getUniqueIndices(AID aid) {
		throw new UnsupportedOperationException("Not implemented");
	}

	public boolean contains(byte[] partialAid) {
		try {
			return atomStoreSupplier.get().contains(partialAid);
		} catch (Exception e) {
			throw new TempoException("Error while querying contains(" + Arrays.toString(partialAid) + ")", e);
		}
	}

	@Override
	public Optional<TempoAtom> get(AID aid) {
		try {
			// TODO awful conversion from legacy 'Atom'
			return atomStoreSupplier.get().getAtom(aid)
				.map(LegacyUtils::fromLegacyAtom);
		} catch (DatabaseException e) {
			throw new TempoException("Error while querying getAtom(" + aid + ")", e);
		}
	}

	@Override
	public Optional<AID> get(long logicalClock) {
		try {
			return Optional.ofNullable(atomStoreSupplier.get().getAtom(logicalClock).getAtomID());
		} catch (DatabaseException e) {
			throw new TempoException("Error while querying getAtom(" + logicalClock + ")", e);
		}
	}

	public List<AID> get(byte[] partialAid) {
		try {
			return atomStoreSupplier.get().get(partialAid);
		} catch (Exception e) {
			throw new TempoException("Error while querying get(" + Arrays.toString(partialAid) + ")", e);
		}
	}

	@Override
	public AtomStoreResult store(TempoAtom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		// TODO remove awful conversion
		final SimpleRadixEngineAtom radixEngineAtom = convertToCMAtom(atom);

		try {
			if (atomStoreSupplier.get().storeAtom(new PreparedAtom(radixEngineAtom, UInt384.ONE)).isCompleted()) {
				return AtomStoreResult.success();
			} else {
				// TODO awful error handling, but legacy store should be removed entirely soon
				throw new LedgerException("Store failed");
			}
		} catch (IOException e) {
			throw new TempoException("Error while storing atom " + atom.getAID(), e);
		}
	}

	@Override
	public AtomStoreResult replace(Set<AID> aids, TempoAtom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		// TODO remove awful conversion
		final SimpleRadixEngineAtom radixEngineAtom = convertToCMAtom(atom);

		try {
			if (atomStoreSupplier.get().replaceAtom(aids, new PreparedAtom(radixEngineAtom, UInt384.ONE)).isCompleted()) {
				return AtomStoreResult.success();
			} else {
				// TODO awful error handling, but legacy store should be removed entirely soon
				throw new LedgerException("Replace failed");
			}
		} catch (IOException e) {
			throw new TempoException("Error while storing atom " + atom.getAID(), e);
		}
	}

	@Override
	public LedgerCursor search(LedgerIndex.LedgerIndexType type, LedgerIndex index, LedgerSearchMode mode) {
		try {
			return atomStoreSupplier.get().search(type, index, mode);
		} catch (DatabaseException e) {
			throw new TempoException("Error while searching for " + index, e);
		}
	}

	@Override
	public boolean contains(LedgerIndex.LedgerIndexType type, LedgerIndex index, LedgerSearchMode mode) {
		// TODO not very efficient but will be removed anyway soon
		return search(type, index, mode) != null;
	}

	@Override
	public ImmutableList<AID> getNext(long logicalClockCursor, int limit) {
		try {
			AtomDiscoveryRequest atomDiscoveryRequest = new AtomDiscoveryRequest(DiscoveryRequest.Action.DISCOVER);
			atomDiscoveryRequest.setLimit((short) 64);
			atomDiscoveryRequest.setCursor(new DiscoveryCursor(logicalClockCursor));
			atomDiscoveryRequest.setShards(LocalSystem.getInstance().getShards());
			atomSyncStoreSupplier.get().discovery(atomDiscoveryRequest);

			ImmutableList<AID> inventory = ImmutableList.copyOf(atomDiscoveryRequest.getInventory());
			return inventory;
		} catch (DiscoveryException e) {
			throw new TempoException("Error while advancing cursor", e);
		}
	}

	@Override
	public void open() {
		// not implemented here as is already done in legacy TempoAtomStore directly
	}

	@Override
	public void close() {
		// not implemented here as is already done in legacy TempoAtomStore directly
	}

	@Override
	public void reset() {
		// not implemented here as is already done in legacy TempoAtomStore directly
	}

	private SimpleRadixEngineAtom convertToCMAtom(TempoAtom atom) {
		try {
			Atom legacyAtom = LegacyUtils.toLegacyAtom(atom);
			return RadixEngineUtils.toCMAtom(legacyAtom);
		} catch (RadixEngineUtils.CMAtomConversionException e) {
			throw new TempoException("Error while converting atom", e);
		}
	}
}
