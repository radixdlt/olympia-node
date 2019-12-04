package com.radixdlt.tempo;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.radixdlt.common.AID;
import com.radixdlt.ledger.Consensus;
import com.radixdlt.ledger.ConsensusObservation;
import com.radixdlt.tempo.delivery.LazyRequestDeliverer;
import com.radixdlt.tempo.discovery.AtomDiscoverer;
import com.radixdlt.tempo.store.LedgerEntryStore;
import com.radixdlt.tempo.store.LedgerEntryStoreView;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.network2.addressbook.Peer;

import java.io.Closeable;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The Tempo implementation of a ledger.
 */
public final class Tempo implements Consensus, Closeable {
	private static final Logger log = Logging.getLogger("tempo");
	private static final int INBOUND_QUEUE_CAPACITY = 16384;

	private final LedgerEntryStore ledgerEntryStore;

	private final Set<AtomDiscoverer> atomDiscoverers;
	private final LazyRequestDeliverer requestDeliverer;
	private final Set<LedgerEntryObserver> observers; // TODO external ledgerObservations and internal observers is ambiguous

	private final BlockingQueue<ConsensusObservation> consensusObservations;

	@Inject
	public Tempo(
		LedgerEntryStore ledgerEntryStore,
		Set<AtomDiscoverer> atomDiscoverers,
		LazyRequestDeliverer requestDeliverer,
		Set<LedgerEntryObserver> observers
	) {
		this.ledgerEntryStore = Objects.requireNonNull(ledgerEntryStore);
		this.atomDiscoverers = Objects.requireNonNull(atomDiscoverers);
		this.requestDeliverer = Objects.requireNonNull(requestDeliverer);
		this.observers = Objects.requireNonNull(observers);

		this.consensusObservations = new LinkedBlockingQueue<>(INBOUND_QUEUE_CAPACITY);

		// hook up components
		for (AtomDiscoverer atomDiscoverer : this.atomDiscoverers) {
			atomDiscoverer.addListener(this::onDiscovered);
		}
	}

	@Override
	public ConsensusObservation observe() throws InterruptedException {
		return this.consensusObservations.take();
	}

	private void onDiscovered(Set<AID> aids, Peer peer) {
		requestDeliverer.deliver(aids, ImmutableSet.of(peer)).forEach((aid, future) -> future.thenAccept(result -> {
			if (result.isSuccess()) {
				injectObservation(ConsensusObservation.adopt(result.getLedgerEntry()));
			}
		}));
	}

	private void injectObservation(ConsensusObservation observation) {
		if (!this.consensusObservations.add(observation)) {
			// TODO more graceful queue full handling
			log.error("Atom observations queue full");
		}
	}

	public void start() {
		Modules.put(LedgerEntryStoreView.class, this.ledgerEntryStore);
	}

	@Override
	public void close() {
		Modules.remove(LedgerEntryStoreView.class);
		this.ledgerEntryStore.close();
		this.requestDeliverer.close();
	}
}
