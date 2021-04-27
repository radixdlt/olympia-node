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

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.atom.TxActionListBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RadixEngineMempool;
import com.radixdlt.statecomputer.transaction.TokenFeeChecker;
import com.radixdlt.utils.UInt256;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Random;

/**
 * Periodically fills the mempool with valid transactions
 */
public final class MempoolFiller {
	private static final Logger logger = LogManager.getLogger();
	private final RadixEngine<LedgerAndBFTProof> radixEngine;

	private final RemoteEventDispatcher<MempoolAdd> remoteMempoolAddEventDispatcher;
	private final EventDispatcher<MempoolAdd> mempoolAddEventDispatcher;

	private final RadixEngineMempool radixEngineMempool;
	private final ScheduledEventDispatcher<ScheduledMempoolFill> mempoolFillDispatcher;
	private final SystemCounters systemCounters;
	private final PeersView peersView;
	private final Random random;
	private final HashSigner hashSigner;
	private final ECPublicKey self;
	private final REAddr account;

	private boolean enabled = false;
	private int numTransactions;
	private boolean sendToSelf = false;

	@Inject
	public MempoolFiller(
		@Self ECPublicKey self,
		@Self REAddr account,
		@Named("RadixEngine") HashSigner hashSigner,
		RadixEngineMempool radixEngineMempool,
		RadixEngine<LedgerAndBFTProof> radixEngine,
		EventDispatcher<MempoolAdd> mempoolAddEventDispatcher,
		RemoteEventDispatcher<MempoolAdd> remoteMempoolAddEventDispatcher,
		ScheduledEventDispatcher<ScheduledMempoolFill> mempoolFillDispatcher,
		PeersView peersView,
		Random random,
		SystemCounters systemCounters
	) {
		this.self = self;
		this.account = account;
		this.hashSigner = hashSigner;
		this.radixEngine = radixEngine;
		this.radixEngineMempool = radixEngineMempool;
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
				u.onError("Already " + (enabled ? "enabled." : "disabled."));
				return;
			}

			logger.info("Mempool Filler: Updating " + u.enabled());
			u.onSuccess();

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

			var particleCount = radixEngine.getComputedState(Integer.class);
			if (particleCount == 0) {
				logger.info("Mempool Filler empty balance");
				return;
			}

			var actions = TxActionListBuilder.create()
				.splitNative(REAddr.ofNativeToken(), TokenFeeChecker.FIXED_FEE.multiply(UInt256.TWO))
				.burn(REAddr.ofNativeToken(), account, TokenFeeChecker.FIXED_FEE)
				.build();

			var shuttingDown = radixEngineMempool.getShuttingDownSubstates();
			var txns = new ArrayList<Txn>();
			for (int i = 0; i < numTransactions; i++) {
				try {
					var builder = radixEngine.construct(self, actions, shuttingDown);
					shuttingDown.addAll(builder.toLowLevelBuilder().remoteDownSubstate());
					var txn = builder.signAndBuild(hashSigner::sign);
					txns.add(txn);
				} catch (TxBuilderException e) {
					break;
				}
			}

			if (txns.size() == 1) {
				logger.info("Mempool Filler mempool: {} Adding txn {} to mempool...",
					systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT),
					txns.get(0).getId()
				);
			} else {
				logger.info("Mempool Filler mempool: {} Adding {} txns to mempool...",
					systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT),
					txns.size()
				);
			}

			final var peers = peersView.peers()
				.map(PeersView.PeerInfo::bftNode)
				.collect(ImmutableList.toImmutableList());
			txns.forEach(txn -> {
				int index = random.nextInt(sendToSelf ? peers.size() + 1 : peers.size());
				var mempoolAdd = MempoolAdd.create(txn);
				if (index == peers.size()) {
					this.mempoolAddEventDispatcher.dispatch(mempoolAdd);
				} else {
					this.remoteMempoolAddEventDispatcher.dispatch(peers.get(index), mempoolAdd);
				}
			});

			mempoolFillDispatcher.dispatch(ScheduledMempoolFill.create(), 500);
		};
	}
}
