/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.integration.distributed.deterministic;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.RateLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.util.Modules;
import com.radixdlt.MockedKeyModule;
import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.liveness.EpochLocalTimeoutOccurrence;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.PacemakerMaxExponent;
import com.radixdlt.consensus.bft.PacemakerRate;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.FunctionalNodeModule;
import com.radixdlt.MockedCryptoModule;
import com.radixdlt.MockedPersistenceStoreModule;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.recovery.MockedRecoveryModule;
import com.radixdlt.integration.distributed.deterministic.configuration.EpochNodeWeightMapping;
import com.radixdlt.integration.distributed.deterministic.configuration.NodeIndexAndWeight;
import com.radixdlt.middleware2.network.GetVerticesRequestRateLimit;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.utils.TimeSupplier;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.sync.MockedCommittedReaderModule;
import com.radixdlt.sync.SyncConfig;
import com.radixdlt.utils.UInt256;

import io.reactivex.rxjava3.schedulers.Timed;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A deterministic test where each event that occurs in the network
 * is emitted and processed synchronously by the caller.
 */
public final class DeterministicTest {
	private final DeterministicNodes nodes;
	private final DeterministicNetwork network;

	private DeterministicTest(
		ImmutableList<BFTNode> nodes,
		MessageSelector messageSelector,
		MessageMutator messageMutator,
		Module baseModule,
		Module overrideModule
	) {
		this.network = new DeterministicNetwork(
			nodes,
			messageSelector,
			messageMutator
		);

		this.nodes = new DeterministicNodes(
			nodes,
			this.network::createSender,
			baseModule,
			overrideModule
		);
	}

	public static class Builder {
		private ImmutableList<BFTNode> nodes = ImmutableList.of(BFTNode.create(ECKeyPair.generateNew().getPublicKey()));
		private MessageSelector messageSelector = MessageSelector.firstSelector();
		private MessageMutator messageMutator = MessageMutator.nothing();
		private long pacemakerTimeout = 1000L;
		private EpochNodeWeightMapping epochNodeWeightMapping = null;
		private Module overrideModule = null;
		private ImmutableList.Builder<Module> modules = ImmutableList.builder();

		private Builder() {
			// Nothing to do here
		}

		public Builder numNodes(int numNodes) {
			this.nodes = Stream.generate(ECKeyPair::generateNew)
				.limit(numNodes)
				.sorted(Comparator.<ECKeyPair, EUID>comparing(k -> k.getPublicKey().euid()).reversed())
				.map(kp -> BFTNode.create(kp.getPublicKey()))
				.collect(ImmutableList.toImmutableList());
			return this;
		}

		/**
		 * Override with an incorrect module which should cause a test to fail.
		 * TODO: Refactor to make the link between incorrect module and failing test
		 * more explicit.
		 *
		 * @param module the incorrect module
		 * @return the current builder
		 */
		public Builder overrideWithIncorrectModule(Module module) {
			this.overrideModule = module;
			return this;
		}

		public Builder epochNodeIndexesMapping(LongFunction<IntStream> epochToNodeIndexesMapping) {
			Objects.requireNonNull(epochToNodeIndexesMapping);
			this.epochNodeWeightMapping = epoch -> equalWeight(epochToNodeIndexesMapping.apply(epoch));
			return this;
		}

		public Builder epochNodeWeightMapping(EpochNodeWeightMapping epochNodeWeightMapping) {
			this.epochNodeWeightMapping = Objects.requireNonNull(epochNodeWeightMapping);
			return this;
		}

		public Builder messageSelector(MessageSelector messageSelector) {
			this.messageSelector = Objects.requireNonNull(messageSelector);
			return this;
		}

		public Builder messageMutator(MessageMutator messageMutator) {
			this.messageMutator = Objects.requireNonNull(messageMutator);
			return this;
		}

		public Builder messageMutators(MessageMutator... messageMutators) {
			final var combinedMutator = Stream.of(messageMutators).reduce(MessageMutator::andThen).get();
			return this.messageMutator(combinedMutator);
		}

		public Builder pacemakerTimeout(long pacemakerTimeout) {
			if (pacemakerTimeout <= 0) {
				throw new IllegalArgumentException("Pacemaker timeout must be positive: " + pacemakerTimeout);
			}
			this.pacemakerTimeout = pacemakerTimeout;
			return this;
		}

		public DeterministicTest buildWithEpochs(View epochHighView) {
			Objects.requireNonNull(epochHighView);
			modules.add(new FunctionalNodeModule(true, true, false, false, false, true, false));
			addEpochedConsensusProcessorModule(epochHighView);
			return build();
		}

		public DeterministicTest buildWithEpochsAndSync(View epochHighView, SyncConfig syncConfig) {
			Objects.requireNonNull(epochHighView);
			modules.add(new FunctionalNodeModule(true, true, false, false, false, true, true));
			modules.add(new MockedCommittedReaderModule());
			modules.add(new AbstractModule() {
				@Provides
				private PeersView peersView(@Self BFTNode self) {
					return () -> nodes.stream()
						.filter(n -> !self.equals(n))
						.map(PeersView.PeerInfo::fromBftNode);
				}

				@Provides
				private SyncConfig syncConfig() {
					return syncConfig;
				}
			});
			addEpochedConsensusProcessorModule(epochHighView);
			return build();
		}

		public DeterministicTest buildWithoutEpochs() {
			modules.add(new FunctionalNodeModule(true, false, false, false, false, false, false));
			addNonEpochedConsensusProcessorModule();
			return build();
		}

		private DeterministicTest build() {
			modules.add(new AbstractModule() {
				@Override
				public void configure() {
					bindConstant().annotatedWith(BFTSyncPatienceMillis.class).to(50);
					bindConstant().annotatedWith(PacemakerTimeout.class).to(pacemakerTimeout);
					bindConstant().annotatedWith(PacemakerRate.class).to(2.0);
					// Use constant timeout for now
					bindConstant().annotatedWith(PacemakerMaxExponent.class).to(0);
					bind(TimeSupplier.class).toInstance(System::currentTimeMillis);
					bind(Random.class).toInstance(new Random(123456));
					bind(RateLimiter.class).annotatedWith(GetVerticesRequestRateLimit.class)
						.toInstance(unlimitedRateLimiter());
				}
			});
			modules.add(new MockedKeyModule());
			modules.add(new MockedCryptoModule());
			modules.add(new MockedPersistenceStoreModule());
			modules.add(new MockedRecoveryModule());

			return new DeterministicTest(
				this.nodes,
				this.messageSelector,
				this.messageMutator,
				Modules.combine(modules.build()),
				overrideModule
			);
		}

		private void addNonEpochedConsensusProcessorModule() {
			final var validatorSet = validatorSetMapping().apply(1L);
			modules.add(new AbstractModule() {
				@Override
				protected void configure() {
					bind(BFTValidatorSet.class).toInstance(validatorSet);
				}
			});
		}

		private void addEpochedConsensusProcessorModule(View epochHighView) {
			// TODO: adapter from LongFunction<BFTValidatorSet> to Function<Long, BFTValidatorSet> shouldn't be needed
			Function<Long, BFTValidatorSet> epochToValidatorSetMapping = validatorSetMapping()::apply;
			modules.add(new AbstractModule() {
				@Override
				public void configure() {
					bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(epochHighView);
					bind(BFTValidatorSet.class).toInstance(epochToValidatorSetMapping.apply(1L));
					bind(new TypeLiteral<EventProcessor<EpochView>>() { }).toInstance(epochView -> { });
					bind(new TypeLiteral<EventProcessor<EpochLocalTimeoutOccurrence>>() { }).toInstance(t -> { });
				}

				@Provides
				public Function<Long, BFTValidatorSet> epochToNodeMapper() {
					return epochToValidatorSetMapping;
				}
			});
		}

		private LongFunction<BFTValidatorSet> validatorSetMapping() {
			return epochNodeWeightMapping == null
				? epoch -> completeEqualWeightValidatorSet(this.nodes)
				: epoch -> partialMixedWeightValidatorSet(epoch, this.nodes, this.epochNodeWeightMapping);
		}

		private static BFTValidatorSet completeEqualWeightValidatorSet(ImmutableList<BFTNode> nodes) {
			return BFTValidatorSet.from(
				nodes.stream()
					.map(node -> BFTValidator.from(node, UInt256.ONE))
			);
		}

		private static BFTValidatorSet partialMixedWeightValidatorSet(
			long epoch,
			ImmutableList<BFTNode> nodes,
			EpochNodeWeightMapping mapper
		) {
			return BFTValidatorSet.from(
				mapper.nodesAndWeightFor(epoch)
					.map(niw -> BFTValidator.from(nodes.get(niw.index()), niw.weight()))
			);
		}

		private static Stream<NodeIndexAndWeight> equalWeight(IntStream indexes) {
			return indexes.mapToObj(i -> NodeIndexAndWeight.from(i, UInt256.ONE));
		}

		private static RateLimiter unlimitedRateLimiter() {
			return RateLimiter.create(Double.MAX_VALUE);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public DeterministicNetwork getNetwork() {
		return this.network;
	}

	public DeterministicNodes getNodes() {
		return this.nodes;
	}

	public interface DeterministicManualExecutor {
		void start();
		void processNext(int senderIndex, int receiverIndex, Class<?> eventClass);
	}

	public DeterministicManualExecutor createExecutor() {
		return new DeterministicManualExecutor() {
			@Override
			public void start() {
				nodes.start();
			}

			@Override
			public void processNext(int senderIndex, int receiverIndex, Class<?> eventClass) {
				Timed<ControlledMessage> nextMsg = network.nextMessage(msg -> msg.channelId().senderIndex() == senderIndex
					&& msg.channelId().receiverIndex() == receiverIndex
					&& eventClass.isInstance(msg.message()));

				nodes.handleMessage(nextMsg);
			}
		};
	}

	public DeterministicTest runForCount(int count) {
		this.nodes.start();

		for (int i = 0; i < count; i++) {
			Timed<ControlledMessage> nextMsg = this.network.nextMessage();
			this.nodes.handleMessage(nextMsg);
		}

		return this;
	}

	public DeterministicTest runUntil(Predicate<Timed<ControlledMessage>> stopPredicate) {
		this.nodes.start();

		while (true) {
			Timed<ControlledMessage> nextMsg = this.network.nextMessage();
			if (stopPredicate.test(nextMsg)) {
				break;
			}

			this.nodes.handleMessage(nextMsg);
		}

		return this;
	}

	/**
	 * Returns a predicate that stops processing messages after a specified number of epochs and
	 * views.
	 *
	 * @param maxEpochView the last epoch and view to process
	 * @return a predicate that halts
	 * 		processing after the specified number of epochs and views
	 */
	public static Predicate<Timed<ControlledMessage>> hasReachedEpochView(EpochView maxEpochView) {
		return timedMsg -> {
			ControlledMessage message = timedMsg.value();
			if (!(message.message() instanceof Proposal)) {
				return false;
			}
			Proposal proposal = (Proposal) message.message();
			EpochView nev = EpochView.of(proposal.getEpoch(), proposal.getView());
			return (nev.compareTo(maxEpochView) > 0);
		};
	}


	/**
	 * Returns a predicate that stops processing messages after a specified number of views.
	 *
	 * @param view the last view to process
	 * @return a predicate that return true after the specified number of views
	 */
	public static Predicate<Timed<ControlledMessage>> hasReachedView(View view) {
		final long maxViewNumber = view.previous().number();
		return timedMsg -> {
			ControlledMessage message = timedMsg.value();
			if (!(message.message() instanceof Proposal)) {
				return false;
			}
			Proposal p = (Proposal) message.message();
			return (p.getView().number() > maxViewNumber);
		};
	}

	public static Predicate<Timed<ControlledMessage>> viewUpdateOnNode(View view, int nodeIndex) {
		return timedMsg -> {
			final var message = timedMsg.value();
			if (!(message.message() instanceof ViewUpdate)) {
				return false;
			}
			final var viewUpdate = (ViewUpdate) message.message();
			return message.channelId().receiverIndex() == nodeIndex
				&& viewUpdate.getCurrentView().gte(view);
		};
	}

	public static Predicate<Timed<ControlledMessage>> ledgerStateVersionOnNode(long stateVersion, int nodeIndex) {
		return timedMsg -> {
			final var message = timedMsg.value();
			if (!(message.message() instanceof LedgerUpdate)) {
				return false;
			}
			final var ledgerUpdate = (LedgerUpdate) message.message();
			return message.channelId().receiverIndex() == nodeIndex
				&& ledgerUpdate.getTail().getStateVersion() >= stateVersion;
		};
	}

	public SystemCounters getSystemCounters(int nodeIndex) {
		return this.nodes.getSystemCounters(nodeIndex);
	}

	public int numNodes() {
		return this.nodes.numNodes();
	}

	// Debugging aid for messages
	public void dumpMessages(PrintStream out) {
		this.network.dumpMessages(out);
	}
}
