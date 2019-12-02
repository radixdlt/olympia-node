package com.radixdlt.tempo;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.ledger.Ledger;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerEntry;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerIndex.LedgerIndexType;
import com.radixdlt.ledger.LedgerObservation;
import com.radixdlt.ledger.LedgerSearchMode;
import com.radixdlt.ledger.exceptions.AtomAlreadyExistsException;
import com.radixdlt.ledger.exceptions.LedgerIndexConflictException;
import com.radixdlt.tempo.consensus.Consensus;
import com.radixdlt.tempo.consensus.ConsensusAction;
import com.radixdlt.tempo.delivery.RequestDeliverer;
import com.radixdlt.tempo.discovery.AtomDiscoverer;
import com.radixdlt.tempo.store.LedgerEntryConflict;
import com.radixdlt.tempo.store.LedgerEntryStoreResult;
import com.radixdlt.tempo.store.LedgerEntryStore;
import com.radixdlt.tempo.store.LedgerEntryStoreView;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.network2.addressbook.Peer;
import org.radix.utils.SimpleThreadPool;

import java.io.Closeable;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The Tempo implementation of a ledger.
 */
public final class Tempo implements Ledger, Closeable {
	private static final Logger log = Logging.getLogger("tempo");
	private static final int INBOUND_QUEUE_CAPACITY = 16384;

	private final EUID self;
	private final LedgerEntryStore ledgerEntryStore;
	private final Consensus consensus;

	private final Set<Resource> ownedResources;
	private final Set<AtomDiscoverer> atomDiscoverers;
	private final RequestDeliverer requestDeliverer;
	private final Set<LedgerEntryObserver> observers; // TODO external ledgerObservations and internal observers is ambiguous

	private final BlockingQueue<LedgerObservation> ledgerObservations;

	@Inject
	public Tempo(
		@Named("self") EUID self,
		LedgerEntryStore ledgerEntryStore,
		Consensus consensus,
		@Owned Set<Resource> ownedResources,
		Set<AtomDiscoverer> atomDiscoverers,
		RequestDeliverer requestDeliverer,
		Set<LedgerEntryObserver> observers
	) {
		this.self = Objects.requireNonNull(self);
		this.ledgerEntryStore = Objects.requireNonNull(ledgerEntryStore);
		this.consensus = Objects.requireNonNull(consensus);
		this.ownedResources = Objects.requireNonNull(ownedResources);
		this.atomDiscoverers = Objects.requireNonNull(atomDiscoverers);
		this.requestDeliverer = Objects.requireNonNull(requestDeliverer);
		this.observers = Objects.requireNonNull(observers);

		this.ledgerObservations = new LinkedBlockingQueue<>(INBOUND_QUEUE_CAPACITY);

		// hook up components
		for (AtomDiscoverer atomDiscoverer : this.atomDiscoverers) {
			atomDiscoverer.addListener(this::onDiscovered);
		}
	}

	@Override
	public LedgerObservation observe() throws InterruptedException {
		return this.ledgerObservations.take();
	}

	@Override
	public Optional<LedgerEntry> get(AID aid) {
		return ledgerEntryStore.get(aid);
	}

	@Override
	public void store(LedgerEntry ledgerEntry, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		preCheckStore(ledgerEntry, uniqueIndices, duplicateIndices);
		LedgerEntryStoreResult status = ledgerEntryStore.store(ledgerEntry, uniqueIndices, duplicateIndices);
		postCheckStore(status);
		onAdopted(ledgerEntry, uniqueIndices, duplicateIndices);
	}

	@Override
	public void replace(Set<AID> aids, LedgerEntry ledgerEntry, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		Objects.requireNonNull(aids, "aids");
		preCheckStore(ledgerEntry, uniqueIndices, duplicateIndices);

		LedgerEntryStoreResult status = ledgerEntryStore.replace(aids, ledgerEntry, uniqueIndices, duplicateIndices);
		postCheckStore(status);
		aids.forEach(this::onDeleted);
		onAdopted(ledgerEntry, uniqueIndices, duplicateIndices);
	}

	private void preCheckStore(LedgerEntry ledgerEntry, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		Objects.requireNonNull(ledgerEntry, "ledgerEntry");
		Objects.requireNonNull(uniqueIndices, "uniqueIndices");
		Objects.requireNonNull(duplicateIndices, "duplicateIndices");
		if (uniqueIndices.isEmpty()) {
			throw new TempoException("Atom '" + ledgerEntry.getAID() + "' must have at least one unique index");
		}
		if (ledgerEntry.getShards().isEmpty()) {
			throw new TempoException("Atom '" + ledgerEntry.getAID() + "' must have at least one shard");
		}
		if (ledgerEntryStore.contains(ledgerEntry.getAID())) {
			throw new AtomAlreadyExistsException(ledgerEntry.getAID());
		}
	}

	private void postCheckStore(LedgerEntryStoreResult status) {
		if (!status.isSuccess()) {
			LedgerEntryConflict conflictInfo = status.getConflictInfo();
			throw new LedgerIndexConflictException(conflictInfo.getLedgerEntry(), conflictInfo.getConflictingLedgerEntries());
		}
	}

	private void onDeleted(AID aid) {
		observers.forEach(observer -> observer.onDeleted(aid));
	}

	private void onDiscovered(Set<AID> aids, Peer peer) {
		requestDeliverer.deliver(aids, ImmutableSet.of(peer)).forEach((aid, future) -> future.thenAccept(result -> {
			if (result.isSuccess()) {
				injectObservation(LedgerObservation.adopt(result.getLedgerEntry()));
			}
		}));
	}

	private void injectObservation(LedgerObservation observation) {
		if (!this.ledgerObservations.add(observation)) {
			// TODO more graceful queue full handling
			log.error("Atom observations queue full");
		}
	}

	private void onAdopted(LedgerEntry ledgerEntry, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		observers.forEach(acceptor -> acceptor.onAdopted(ledgerEntry, uniqueIndices, duplicateIndices));
	}

	@Override
	public LedgerCursor search(LedgerIndexType type, LedgerIndex index, LedgerSearchMode mode) {
		return ledgerEntryStore.search(type, index, mode);
	}

	@Override
	public boolean contains(LedgerIndexType type, LedgerIndex index, LedgerSearchMode mode) {
		return ledgerEntryStore.contains(type, index, mode);
	}

	@Override
	public boolean contains(AID aid) {
		return ledgerEntryStore.contains(aid);
	}

	public void start() {
		Modules.put(LedgerEntryStoreView.class, this.ledgerEntryStore);
	}

	@Override
	public void close() {
		Modules.remove(LedgerEntryStoreView.class);
		this.ownedResources.forEach(Resource::close);
	}

	public void reset() {
		this.ownedResources.forEach(Resource::reset);
	}
}
