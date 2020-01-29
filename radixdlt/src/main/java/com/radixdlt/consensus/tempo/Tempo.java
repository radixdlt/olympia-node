/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.tempo;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.radixdlt.common.AID;
import com.radixdlt.consensus.Consensus;
import com.radixdlt.consensus.ConsensusObservation;
import com.radixdlt.delivery.LazyRequestDeliverer;
import com.radixdlt.discovery.AtomDiscoverer;
import com.radixdlt.store.LedgerEntry;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network2.addressbook.Peer;
import org.radix.utils.SimpleThreadPool;

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

	private final LazyRequestDeliverer requestDeliverer;

	private final BlockingQueue<ConsensusObservation> consensusObservations;
	private final SimpleThreadPool<LedgerEntry> consensusThreadPool;

	@Inject
	public Tempo(
		Application application,
		Set<AtomDiscoverer> atomDiscoverers,
		LazyRequestDeliverer requestDeliverer
	) {
		Objects.requireNonNull(application);
		Objects.requireNonNull(atomDiscoverers);
		this.requestDeliverer = Objects.requireNonNull(requestDeliverer);

		this.consensusObservations = new LinkedBlockingQueue<>(INBOUND_QUEUE_CAPACITY);

		// hook up components
		for (AtomDiscoverer atomDiscoverer : atomDiscoverers) {
			atomDiscoverer.addListener(this::onDiscovered);
		}

		this.consensusThreadPool = new SimpleThreadPool<>("Consensus", 1, application::takeNextEntry, this::doConsensus, log);
		this.consensusThreadPool.start();
	}

	private void doConsensus(LedgerEntry entry) {
		// stupid simple "consensus", just immediately commit anything we get our hands on
		this.consensusObservations.add(ConsensusObservation.commit(entry));
	}

	@Override
	public ConsensusObservation observe() throws InterruptedException {
		return this.consensusObservations.take();
	}

	private void onDiscovered(Set<AID> aids, Peer peer) {
		requestDeliverer.deliver(aids, ImmutableSet.of(peer)).forEach((aid, future) -> future.thenAccept(result -> {
			if (result.isSuccess()) {
				injectObservation(ConsensusObservation.commit(result.getLedgerEntry()));
			}
		}));
	}

	private void injectObservation(ConsensusObservation observation) {
		if (!this.consensusObservations.add(observation)) {
			// TODO more graceful queue full handling
			log.error("Atom observations queue full");
		}
	}

	@Override
	public void close() {
		this.requestDeliverer.close();
		this.consensusThreadPool.stop();
	}
}
