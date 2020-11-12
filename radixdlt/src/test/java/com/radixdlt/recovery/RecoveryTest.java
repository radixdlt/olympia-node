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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.radixdlt.ConsensusModule;
import com.radixdlt.CryptoModule;
import com.radixdlt.DispatcherModule;
import com.radixdlt.EpochsConsensusModule;
import com.radixdlt.EpochsLedgerUpdateModule;
import com.radixdlt.EpochsSyncModule;
import com.radixdlt.LedgerCommandGeneratorModule;
import com.radixdlt.LedgerModule;
import com.radixdlt.NoFeeModule;
import com.radixdlt.PersistenceModule;
import com.radixdlt.RadixEngineModule;
import com.radixdlt.RadixEngineStoreModule;
import com.radixdlt.SyncServiceModule;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.PacemakerMaxExponent;
import com.radixdlt.consensus.bft.PacemakerRate;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.MockedCheckpointModule;
import com.radixdlt.environment.deterministic.DeterministicEpochInfo;
import com.radixdlt.mempool.EmptyMempool;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.DeterministicEpochsConsensusProcessor;
import com.radixdlt.environment.deterministic.DeterministicMessageSenderModule;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.ControlledSenderFactory;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.MinValidators;
import com.radixdlt.statecomputer.RadixEngineStateComputer.CommittedAtomSender;
import com.radixdlt.store.LastEpochProof;
import com.radixdlt.sync.LocalSyncServiceAccumulatorProcessor.SyncTimeoutScheduler;
import com.radixdlt.sync.SyncPatienceMillis;
import com.radixdlt.utils.UInt256;
import io.reactivex.rxjava3.schedulers.Timed;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.cli.ParseException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Verifies that on restarts (simulated via creation of new injectors) that the application
 * state is the same as last seen.
 */
@RunWith(Parameterized.class)
public class RecoveryTest {

	@Parameters
	public static Collection<Object[]> parameters() {
		return List.of(new Object[][] {
			{10L}, {1000000L}
		});
	}

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private DeterministicNetwork network;
	private Injector currentInjector;
	private ECKeyPair ecKeyPair = ECKeyPair.generateNew();
	private final long epochCeilingView;

	public RecoveryTest(long epochCeilingView) {
		this.epochCeilingView = epochCeilingView;
		this.network = new DeterministicNetwork(
			List.of(BFTNode.create(ecKeyPair.getPublicKey())),
			MessageSelector.firstSelector(),
			MessageMutator.nothing()
		);
	}

	@Before
	public void setup() {
		this.currentInjector = createRunner(ecKeyPair);
		this.currentInjector.getInstance(DeterministicEpochsConsensusProcessor.class).start();
	}

	private Injector createRunner(ECKeyPair ecKeyPair) {
		final BFTNode self = BFTNode.create(ecKeyPair.getPublicKey());

		return Guice.createInjector(
			new AbstractModule() {
				@Override
				protected void configure() {
					bind(HashSigner.class).toInstance(ecKeyPair::sign);
					bind(BFTNode.class).annotatedWith(Self.class).toInstance(self);
					bindConstant().annotatedWith(Names.named("magic")).to(0);
					bind(ControlledSenderFactory.class).toInstance(network::createSender);

					bind(Integer.class).annotatedWith(SyncPatienceMillis.class).toInstance(200);
					bind(Integer.class).annotatedWith(BFTSyncPatienceMillis.class).toInstance(200);
					bind(Integer.class).annotatedWith(MinValidators.class).toInstance(1);
					bind(Long.class).annotatedWith(PacemakerTimeout.class).toInstance(1000L);
					bind(Double.class).annotatedWith(PacemakerRate.class).toInstance(2.0);
					bind(Integer.class).annotatedWith(PacemakerMaxExponent.class).toInstance(6);
					bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(View.of(epochCeilingView));

					// System
					bind(Mempool.class).to(EmptyMempool.class);
					bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
					bind(TimeSupplier.class).toInstance(System::currentTimeMillis);

					// TODO: Move these into DeterministicSender
					bind(CommittedAtomSender.class).toInstance(atom -> { });
					bind(SyncTimeoutScheduler.class).toInstance((syncInProgress, milliseconds) -> { });

					final RuntimeProperties runtimeProperties;
					// TODO: this constructor/class/inheritance/dependency is horribly broken
					try {
						runtimeProperties = new RuntimeProperties(new JSONObject(), new String[0]);
						runtimeProperties.set("db.location", folder.getRoot().getAbsolutePath() + "/RADIXDB_RECOVERY_TEST_" + self);
					} catch (ParseException e) {
						throw new IllegalStateException();
					}
					bind(RuntimeProperties.class).toInstance(runtimeProperties);
				}
			},

			new MockedCheckpointModule(BFTValidatorSet.from(Stream.of(BFTValidator.from(self, UInt256.ONE)))),

			new DeterministicMessageSenderModule(),

			new DispatcherModule(),

			// Consensus
			new CryptoModule(),
			new ConsensusModule(),

			// Ledger
			new LedgerModule(),
			new LedgerCommandGeneratorModule(),

			// Sync
			new SyncServiceModule(),

			// Epochs - Consensus
			new EpochsConsensusModule(),
			// Epochs - Ledger
			new EpochsLedgerUpdateModule(),
			// Epochs - Sync
			new EpochsSyncModule(),

			// State Computer
			new RadixEngineModule(),
			new RadixEngineStoreModule(),

			// Fees
			new NoFeeModule(),

			new PersistenceModule()
		);
	}

	private RadixEngine<LedgerAtom> getRadixEngine() {
		return currentInjector.getInstance(Key.get(new TypeLiteral<RadixEngine<LedgerAtom>>() { }));
	}

	private CommittedAtomsStore getAtomStore() {
		return currentInjector.getInstance(CommittedAtomsStore.class);
	}

	private DeterministicEpochInfo getEpochInfo() {
		return currentInjector.getInstance(DeterministicEpochInfo.class);
	}

	private void restartNode() {
		this.network.dropMessages(m -> m.channelId().receiverIndex() == 0 && m.channelId().senderIndex() == 0);
		this.currentInjector = createRunner(ecKeyPair);
		DeterministicEpochsConsensusProcessor processor = currentInjector.getInstance(DeterministicEpochsConsensusProcessor.class);
		processor.start();
	}

	private void processForCount(int messageCount) {
		for (int i = 0; i < messageCount; i++) {
			Timed<ControlledMessage> msg = this.network.nextMessage();
			DeterministicEpochsConsensusProcessor runner = currentInjector
				.getInstance(DeterministicEpochsConsensusProcessor.class);
			runner.handleMessage(msg.value().origin(), msg.value().message());
		}
	}

	@Test
	public void on_reboot_should_load_same_computed_state() {
		// Arrange
		processForCount(100);
		RadixEngine<LedgerAtom> radixEngine = getRadixEngine();
		SystemParticle systemParticle = radixEngine.getComputedState(SystemParticle.class);

		// Act
		restartNode();

		// Assert
		RadixEngine<LedgerAtom> restartedRadixEngine = getRadixEngine();
		SystemParticle restartedSystemParticle = restartedRadixEngine.getComputedState(SystemParticle.class);
		assertThat(restartedSystemParticle).isEqualTo(systemParticle);
	}

	@Test
	public void on_reboot_should_load_same_last_header() {
		// Arrange
		processForCount(100);
		CommittedAtomsStore atomStore = getAtomStore();
		Optional<VerifiedLedgerHeaderAndProof> proof = atomStore.getLastVerifiedHeader();

		// Act
		restartNode();

		// Assert
		CommittedAtomsStore restartedAtomStore = getAtomStore();
		Optional<VerifiedLedgerHeaderAndProof> restartedProof = restartedAtomStore.getLastVerifiedHeader();
		assertThat(restartedProof).isEqualTo(proof);
	}

	@Test
	public void on_reboot_should_load_same_last_epoch_header() {
		// Arrange
		processForCount(100);
		DeterministicEpochInfo epochInfo = getEpochInfo();
		EpochView epochView = epochInfo.getCurrentEpochView();

		// Act
		restartNode();

		// Assert
		VerifiedLedgerHeaderAndProof restartedEpochProof = currentInjector.getInstance(
			Key.get(VerifiedLedgerHeaderAndProof.class, LastEpochProof.class)
		);
		assertThat(restartedEpochProof.isEndOfEpoch()).isTrue();
		assertThat(restartedEpochProof.getEpoch()).isEqualTo(epochView.getEpoch() - 1);
	}
}
