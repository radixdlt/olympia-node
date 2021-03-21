/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.chaos.mempoolfiller;

import com.google.common.hash.HashCode;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.atom.AtomBuilder;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.LedgerAtom;
import com.radixdlt.network.addressbook.PeersView;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Random;

/**
 * Periodically fills the mempool with valid transactions
 */
public final class MempoolFiller {
	private static final Logger logger = LogManager.getLogger();
	private final Serialization serialization;
	private final RadixEngine<LedgerAtom, LedgerProof> radixEngine;

	private final RemoteEventDispatcher<MempoolAdd> remoteMempoolAddEventDispatcher;
	private final EventDispatcher<MempoolAdd> mempoolAddEventDispatcher;
	private final ScheduledEventDispatcher<ScheduledMempoolFill> mempoolFillDispatcher;
	private final SystemCounters systemCounters;
	private final PeersView peersView;
	private final Random random;
	private final HashSigner hashSigner;
	private final RadixAddress selfAddress;

	private boolean enabled = false;
	private int numTransactions;
	private boolean sendToSelf = false;

	@Inject
	public MempoolFiller(
		@Self RadixAddress selfAddress,
		@Named("RadixEngine") HashSigner hashSigner,
		Serialization serialization,
		RadixEngine<LedgerAtom, LedgerProof> radixEngine,
		EventDispatcher<MempoolAdd> mempoolAddEventDispatcher,
		RemoteEventDispatcher<MempoolAdd> remoteMempoolAddEventDispatcher,
		ScheduledEventDispatcher<ScheduledMempoolFill> mempoolFillDispatcher,
		PeersView peersView,
		Random random,
		SystemCounters systemCounters
	) {
		this.selfAddress = selfAddress;
		this.hashSigner = hashSigner;
		this.serialization = serialization;
		this.radixEngine = radixEngine;
		this.mempoolAddEventDispatcher = mempoolAddEventDispatcher;
		this.remoteMempoolAddEventDispatcher = remoteMempoolAddEventDispatcher;
		this.mempoolFillDispatcher = mempoolFillDispatcher;
		this.peersView = peersView;
		this.random = random;
		this.systemCounters = systemCounters;
	}

	public EventProcessor<MempoolFillerUpdate> mempoolFillerUpdateEventProcessor() {
		return u -> {
			u.numTransactions().ifPresent(numTx -> this.numTransactions = numTx);
			u.sendToSelf().ifPresent(sendToSelf -> this.sendToSelf = sendToSelf);

			if (u.enabled() == enabled) {
				return;
			}

			logger.info("Mempool Filler: Updating " + u.enabled());

			if (u.enabled()) {
				enabled = true;
				mempoolFillDispatcher.dispatch(ScheduledMempoolFill.create(), 50);
			} else {
				enabled = false;
			}
		};
	}

	public EventProcessor<ScheduledMempoolFill> scheduledMempoolFillEventProcessor() {
		return p -> {
			if (!enabled) {
				return;
			}

			InMemoryWallet wallet = radixEngine.getComputedState(InMemoryWallet.class);
			List<AtomBuilder> atoms = wallet.createParallelTransactions(selfAddress, numTransactions);
			logger.info("Mempool Filler (mempool: {} balance: {} particles: {}): Adding {} atoms to mempool...",
				systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT),
				wallet.getBalance(),
				wallet.getNumParticles(),
				atoms.size()
			);

			List<BFTNode> peers = peersView.peers();
			atoms.forEach(atom -> {
				HashCode hashToSign = atom.computeHashToSign();
				atom.setSignature(selfAddress.euid(), hashSigner.sign(hashToSign));
				Atom clientAtom = atom.buildAtom();
				byte[] payload = serialization.toDson(clientAtom, DsonOutput.Output.ALL);
				Command command = new Command(payload);

				int index = random.nextInt(sendToSelf ? peers.size() + 1 : peers.size());
				if (index == peers.size()) {
					this.mempoolAddEventDispatcher.dispatch(MempoolAdd.create(command));
				} else {
					this.remoteMempoolAddEventDispatcher.dispatch(peers.get(index), MempoolAdd.create(command));
				}
			});

			mempoolFillDispatcher.dispatch(ScheduledMempoolFill.create(), 500);
		};
	}
}
