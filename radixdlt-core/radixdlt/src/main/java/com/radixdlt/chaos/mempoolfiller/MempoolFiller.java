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

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.SubstateStore;
import com.radixdlt.atom.Txn;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.network.addressbook.PeersView;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.utils.UInt256;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Periodically fills the mempool with valid transactions
 */
public final class MempoolFiller {
	private static final Logger logger = LogManager.getLogger();
	private final RadixEngine<LedgerAndBFTProof> radixEngine;

	private final RemoteEventDispatcher<MempoolAdd> remoteMempoolAddEventDispatcher;
	private final EventDispatcher<MempoolAdd> mempoolAddEventDispatcher;
	private final ScheduledEventDispatcher<ScheduledMempoolFill> mempoolFillDispatcher;
	private final SystemCounters systemCounters;
	private final PeersView peersView;
	private final Random random;
	private final HashSigner hashSigner;
	private final RadixAddress selfAddress;
	private final RRI nativeToken;
	private final UInt256 fee = UInt256.TEN.pow(TokenDefinitionUtils.SUB_UNITS_POW_10 - 3).multiply(UInt256.from(50));

	private boolean enabled = false;
	private int numTransactions;
	private boolean sendToSelf = false;

	@Inject
	public MempoolFiller(
		@Self RadixAddress selfAddress,
		@NativeToken RRI nativeToken,
		@Named("RadixEngine") HashSigner hashSigner,
		RadixEngine<LedgerAndBFTProof> radixEngine,
		EventDispatcher<MempoolAdd> mempoolAddEventDispatcher,
		RemoteEventDispatcher<MempoolAdd> remoteMempoolAddEventDispatcher,
		ScheduledEventDispatcher<ScheduledMempoolFill> mempoolFillDispatcher,
		PeersView peersView,
		Random random,
		SystemCounters systemCounters
	) {
		this.selfAddress = selfAddress;
		this.nativeToken = nativeToken;
		this.hashSigner = hashSigner;
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

	private Optional<Txn> createTxn(SubstateStore substateStore, int index, Consumer<SubstateStore> nextSubstate) {
		try {
			var txn = TxBuilder.newBuilder(selfAddress, substateStore)
				.splitNative(nativeToken, fee.multiply(UInt256.TWO), index)
				.burnForFee(nativeToken, fee)
				.signAndBuildRemoteSubstateOnly(hashSigner::sign, nextSubstate);
			return Optional.of(txn);
		} catch (TxBuilderException e) {
			return Optional.empty();
		}
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
			final List<Txn> txns = radixEngine.accessSubstateStoreCache(
				substateStore -> {
					var list = new ArrayList<Txn>();
					var substateHolder = new AtomicReference<>(substateStore);
					for (int i = 0; i < numTransactions; i++) {
						var index = random.nextInt(Math.min(particleCount, 500));
						var maybeTxn = createTxn(substateHolder.get(), index, substateHolder::set);
						if (maybeTxn.isEmpty()) {
							break;
						}
						list.add(maybeTxn.get());
					}
					return list;
				}
			);

			if (txns.size() == 1) {
				logger.info("Mempool Filler mempool: {} Adding atom {} to mempool...",
					systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT),
					txns.get(0).getId()
				);
			} else {
				logger.info("Mempool Filler mempool: {} Adding {} atoms to mempool...",
					systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT),
					txns.size()
				);
			}

			List<BFTNode> peers = peersView.peers();
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
