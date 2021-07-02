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

package com.radixdlt.recovery;

import com.radixdlt.application.tokens.Amount;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.application.system.FeeTable;
import com.radixdlt.statecomputer.forks.ForkManagerModule;
import com.radixdlt.statecomputer.forks.MainnetForksModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import org.assertj.core.api.Condition;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.CryptoModule;
import com.radixdlt.PersistedNodeForTestingModule;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.consensus.epoch.Epoched;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.EventProcessorOnDispatch;
import com.radixdlt.environment.deterministic.ControlledSenderFactory;
import com.radixdlt.environment.deterministic.DeterministicProcessor;
import com.radixdlt.environment.deterministic.DeterministicSavedLastEvent;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.ledger.SimpleLedgerAccumulatorAndVerifier;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RadixEngineModule;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.store.LastEpochProof;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.sync.CommittedReader;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

import io.reactivex.rxjava3.schedulers.Timed;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that on restarts (simulated via creation of new injectors) that the application
 * state is the same as last seen.
 */
@RunWith(Parameterized.class)
public class RecoveryTest {

	@Parameters
	public static Collection<Object[]> parameters() {
		return List.of(
			new Object[]{10L, 80},
			new Object[]{10L, 90},
			new Object[]{10L, 100},
			new Object[]{10L, 500},
			new Object[]{1000000L, 100}
		);
	}

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private DeterministicNetwork network;
	private Injector currentInjector;
	private ECKeyPair ecKeyPair = ECKeyPair.generateNew();
	private final long epochCeilingView;
	private final int processForCount;

	@Inject
	@Genesis
	private VerifiedTxnsAndProof genesisTxns;

	public RecoveryTest(long epochCeilingView, int processForCount) {
		this.epochCeilingView = epochCeilingView;
		this.processForCount = processForCount;
		this.network = new DeterministicNetwork(
			List.of(BFTNode.create(ecKeyPair.getPublicKey())),
			MessageSelector.firstSelector(),
			MessageMutator.nothing()
		);
	}

	@Before
	public void setup() {
		Guice.createInjector(
			new MockedGenesisModule(),
			new CryptoModule(),
			new ForkManagerModule(),
			new MainnetForksModule(),
			new RadixEngineForksLatestOnlyModule(
				new RERulesConfig(
					FeeTable.noFees(),
					OptionalInt.of(50),
					100L,
					2,
					Amount.ofTokens(10),
					1,
					Amount.ofTokens(10),
					9800,
					10
				)),
			new RadixEngineModule(),
			new AbstractModule() {
				@Override
				public void configure() {
					// HACK
					bind(CommittedReader.class).toInstance(CommittedReader.mocked());
					bind(new TypeLiteral<EngineStore<LedgerAndBFTProof>>() { }).toInstance(new InMemoryEngineStore<>());
					bind(SystemCounters.class).toInstance(new SystemCountersImpl());
					bind(new TypeLiteral<ImmutableList<ECPublicKey>>() { }).annotatedWith(Genesis.class)
						.toInstance(ImmutableList.of(ecKeyPair.getPublicKey()));
					bind(LedgerAccumulator.class).to(SimpleLedgerAccumulatorAndVerifier.class);
				}
			}
		).injectMembers(this);

		this.currentInjector = createRunner(ecKeyPair);
		this.currentInjector.getInstance(DeterministicProcessor.class).start();
	}

	@After
	public void teardown() {
		if (this.currentInjector != null) {
			this.currentInjector.getInstance(BerkeleyLedgerEntryStore.class).close();
			this.currentInjector.getInstance(PersistentSafetyStateStore.class).close();
			this.currentInjector.getInstance(DatabaseEnvironment.class).stop();
		}
	}

	private Injector createRunner(ECKeyPair ecKeyPair) {
		final BFTNode self = BFTNode.create(ecKeyPair.getPublicKey());

		return Guice.createInjector(
			new RadixEngineForksLatestOnlyModule(
				new RERulesConfig(
					FeeTable.noFees(),
					OptionalInt.of(50),
					epochCeilingView,
					2,
					Amount.ofTokens(10),
					1,
					Amount.ofTokens(10),
					9800,
					10
				)),
			new ForkManagerModule(),
			new MainnetForksModule(),
			MempoolConfig.asModule(10, 10),
			new AbstractModule() {
				@Override
				protected void configure() {
					bind(VerifiedTxnsAndProof.class).annotatedWith(Genesis.class).toInstance(genesisTxns);
					bind(PeersView.class).toInstance(Stream::of);
					bind(ECKeyPair.class).annotatedWith(Self.class).toInstance(ecKeyPair);
					bind(new TypeLiteral<List<BFTNode>>() { }).toInstance(ImmutableList.of(self));
					bind(ControlledSenderFactory.class).toInstance(network::createSender);
					bindConstant().annotatedWith(DatabaseLocation.class)
						.to(folder.getRoot().getAbsolutePath() + "/RADIXDB_RECOVERY_TEST_" + self);
					bind(new TypeLiteral<DeterministicSavedLastEvent<Vote>>() { })
						.toInstance(new DeterministicSavedLastEvent<>(Vote.class));
					Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessorOnDispatch<?>>() { })
						.addBinding().toProvider(new TypeLiteral<DeterministicSavedLastEvent<Vote>>() { });
				}
			},
			new PersistedNodeForTestingModule()
		);
	}

	private RadixEngine<LedgerAndBFTProof> getRadixEngine() {
		return currentInjector.getInstance(Key.get(new TypeLiteral<RadixEngine<LedgerAndBFTProof>>() { }));
	}

	private CommittedReader getCommittedReader() {
		return currentInjector.getInstance(CommittedReader.class);
	}

	private EpochView getLastEpochView() {
		return currentInjector.getInstance(Key.get(new TypeLiteral<DeterministicSavedLastEvent<EpochViewUpdate>>() { }))
			.getLastEvent().getEpochView();
	}

	private Vote getLastVote() {
		return currentInjector.getInstance(Key.get(new TypeLiteral<DeterministicSavedLastEvent<Vote>>() { })).getLastEvent();
	}

	private void restartNode() {
		this.network.dropMessages(m -> m.channelId().receiverIndex() == 0 && m.channelId().senderIndex() == 0);
		this.currentInjector.getInstance(BerkeleyLedgerEntryStore.class).close();
		this.currentInjector.getInstance(PersistentSafetyStateStore.class).close();
		this.currentInjector.getInstance(DatabaseEnvironment.class).stop();
		this.currentInjector = createRunner(ecKeyPair);
		var processor = currentInjector.getInstance(DeterministicProcessor.class);
		processor.start();
	}

	private void processForCount(int messageCount) {
		for (int i = 0; i < messageCount; i++) {
			Timed<ControlledMessage> msg = this.network.nextMessage();
			var runner = currentInjector.getInstance(DeterministicProcessor.class);
			runner.handleMessage(msg.value().origin(), msg.value().message(), msg.value().typeLiteral());
		}
	}

	@Test
	public void on_reboot_should_load_same_computed_state() {
		// Arrange
		processForCount(processForCount);
		var radixEngine = getRadixEngine();
		var epochView = radixEngine.getComputedState(EpochView.class);

		// Act
		restartNode();

		// Assert
		var restartedRadixEngine = getRadixEngine();
		var restartedEpochView = restartedRadixEngine.getComputedState(EpochView.class);
		assertThat(restartedEpochView).isEqualTo(epochView);
	}

	@Test
	public void on_reboot_should_load_same_last_header() {
		// Arrange
		processForCount(processForCount);
		var reader = getCommittedReader();
		Optional<LedgerProof> proof = reader.getLastProof();

		// Act
		restartNode();

		// Assert
		var restartedReader = getCommittedReader();
		Optional<LedgerProof> restartedProof = restartedReader.getLastProof();
		assertThat(restartedProof).isEqualTo(proof);
	}

	@Test
	public void on_reboot_should_load_same_last_epoch_header() {
		// Arrange
		processForCount(processForCount);
		var epochView = getLastEpochView();

		// Act
		restartNode();

		// Assert
		LedgerProof restartedEpochProof = currentInjector.getInstance(
			Key.get(LedgerProof.class, LastEpochProof.class)
		);

		assertThat(restartedEpochProof.isEndOfEpoch()).isTrue();
		assertThat(
			restartedEpochProof.getEpoch() == epochView.getEpoch() - 1
				|| (restartedEpochProof.getEpoch() == epochView.getEpoch()
				&& epochView.getView().number() > epochCeilingView + 3)
		).isTrue();
	}

	@Test
	public void on_reboot_should_load_same_last_vote() {
		// Arrange
		processForCount(processForCount);
		Vote vote = getLastVote();

		// Act
		restartNode();

		// Assert
		SafetyState safetyState = currentInjector.getInstance(SafetyState.class);
		assertThat(
			safetyState.getLastVotedView().equals(vote.getView())
			|| (safetyState.getLastVotedView().equals(View.genesis())
			&& vote.getView().equals(View.of(epochCeilingView + 3)))
		).isTrue();
	}

	@Test
	public void on_reboot_should_only_emit_pacemaker_events() {
		// Arrange
		processForCount(processForCount);

		// Act
		restartNode();

		// Assert
		assertThat(network.allMessages())
			.hasSize(3)
			.haveExactly(
				1,
				new Condition<>(
					msg -> Epoched.isInstance(msg.message(), ScheduledLocalTimeout.class),
					"A single epoched scheduled timeout has been emitted"
				)
			)
			.haveExactly(
				1,
				new Condition<>(
					msg -> msg.message() instanceof ScheduledLocalTimeout,
					"A single scheduled timeout update has been emitted"
				)
			)
			.haveExactly(
				1,
				new Condition<>(
					msg -> msg.message() instanceof Proposal,
					"A proposal has been emitted"
				)
			);
	}
}
