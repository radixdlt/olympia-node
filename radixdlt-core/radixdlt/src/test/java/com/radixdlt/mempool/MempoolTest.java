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

package com.radixdlt.mempool;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import com.radixdlt.CryptoModule;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.DispatcherModule;
import com.radixdlt.LedgerCommandGeneratorModule;
import com.radixdlt.LedgerLocalMempoolModule;
import com.radixdlt.LedgerModule;
import com.radixdlt.LedgerRecoveryModule;
import com.radixdlt.MempoolRelayerModule;
import com.radixdlt.NoFeeModule;
import com.radixdlt.PersistenceModule;
import com.radixdlt.RadixEngineModule;
import com.radixdlt.RadixEngineStoreModule;
import com.radixdlt.RadixEngineValidatorComputersModule;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.atommodel.AtomAlreadySignedException;
import com.radixdlt.atommodel.unique.UniqueParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.PacemakerMaxExponent;
import com.radixdlt.consensus.bft.PacemakerRate;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.MockedCheckpointModule;
import com.radixdlt.environment.ProcessOnDispatch;
import com.radixdlt.environment.deterministic.ControlledSenderFactory;
import com.radixdlt.environment.deterministic.DeterministicEnvironmentModule;
import com.radixdlt.environment.deterministic.DeterministicMempoolProcessor;
import com.radixdlt.environment.deterministic.DeterministicSavedLastEvent;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.StateComputerLedger.LedgerUpdateSender;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.MaxValidators;
import com.radixdlt.statecomputer.MinValidators;
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.statecomputer.RadixEngineStateComputer.CommittedAtomSender;
import com.radixdlt.sync.SyncPatienceMillis;
import java.util.List;
import org.apache.commons.cli.ParseException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MempoolTest {
	public static Module create() {
		final RadixAddress nativeTokenAddress = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
		final RRI nativeToken = RRI.of(nativeTokenAddress, "NOSUCHTOKEN");
		return Modules.combine(
			new AbstractModule() {
				@Override
				public void configure() {
					bindConstant().annotatedWith(Names.named("magic")).to(0);
					bind(Integer.class).annotatedWith(SyncPatienceMillis.class).toInstance(200);
					bind(Integer.class).annotatedWith(BFTSyncPatienceMillis.class).toInstance(200);
					bind(Integer.class).annotatedWith(MinValidators.class).toInstance(1);
					bind(Integer.class).annotatedWith(MaxValidators.class).toInstance(Integer.MAX_VALUE);
					bind(Long.class).annotatedWith(PacemakerTimeout.class).toInstance(1000L);
					bind(Double.class).annotatedWith(PacemakerRate.class).toInstance(2.0);
					bind(Integer.class).annotatedWith(PacemakerMaxExponent.class).toInstance(6);
					bind(RRI.class).annotatedWith(NativeToken.class).toInstance(nativeToken);

					// System
					bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
					bind(TimeSupplier.class).toInstance(System::currentTimeMillis);

					// TODO: Move these into DeterministicSender
					bind(CommittedAtomSender.class).toInstance(atom -> {
					});
				}
			},
			new MockedCheckpointModule(),

			new DeterministicEnvironmentModule(),
			new DispatcherModule(),

			// Consensus
			new CryptoModule(),

			// Ledger
			new LedgerModule(),
			new LedgerCommandGeneratorModule(),

			// Mempool
			new MempoolRelayerModule(),
			new LedgerLocalMempoolModule(2),

			// State Computer
			new RadixEngineModule(),
			new RadixEngineStoreModule(),
			new RadixEngineValidatorComputersModule(),

			// Fees
			new NoFeeModule(),

			new PersistenceModule(),
			new LedgerRecoveryModule()
		);
	}

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private DeterministicNetwork network;
	private ECKeyPair ecKeyPair = ECKeyPair.generateNew();

	public MempoolTest() {
		this.network = new DeterministicNetwork(
			List.of(BFTNode.create(ecKeyPair.getPublicKey())),
			MessageSelector.firstSelector(),
			MessageMutator.nothing()
		);
	}

	private Injector getInjector(ECKeyPair ecKeyPair) {
		final BFTNode self = BFTNode.create(ecKeyPair.getPublicKey());

		return Guice.createInjector(
			new AbstractModule() {
				@Override
				protected void configure() {
					bind(HashSigner.class).toInstance(ecKeyPair::sign);
					bind(BFTNode.class).annotatedWith(Self.class).toInstance(self);
					bind(new TypeLiteral<List<BFTNode>>() { }).toInstance(ImmutableList.of(self));
					bind(ControlledSenderFactory.class).toInstance(network::createSender);
					bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(View.of(1L));
					bind(LedgerUpdateSender.class).toInstance(mock(LedgerUpdateSender.class));
					AddressBook addressBook = mock(AddressBook.class);
					bind(AddressBook.class).toInstance(addressBook);

					final RuntimeProperties runtimeProperties;
					// TODO: this constructor/class/inheritance/dependency is horribly broken
					try {
						runtimeProperties = new RuntimeProperties(new JSONObject(), new String[0]);
						runtimeProperties.set(
							"db.location",
							folder.getRoot().getAbsolutePath() + "/RADIXDB_RECOVERY_TEST_" + self
						);
					} catch (ParseException e) {
						throw new IllegalStateException();
					}
					bind(RuntimeProperties.class).toInstance(runtimeProperties);

					Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<Vote>>() { }, ProcessOnDispatch.class)
						.addBinding().to(new TypeLiteral<DeterministicSavedLastEvent<Vote>>() { });
					bind(new TypeLiteral<DeterministicSavedLastEvent<Vote>>() { }).in(Scopes.SINGLETON);
				}
			},
			create()
		);
	}

	private static ClientAtom createAtom(ECKeyPair keyPair, Hasher hasher, int nonce, int numParticles) {
		RadixAddress address = new RadixAddress((byte) 0, keyPair.getPublicKey());

		ParticleGroup.ParticleGroupBuilder builder = ParticleGroup.builder();
		for (int i = 0; i < numParticles; i++) {
			RRI rri = RRI.of(address, "test" + i);
			RRIParticle rriParticle = new RRIParticle(rri, nonce);
			UniqueParticle uniqueParticle = new UniqueParticle("test" + i, address, nonce + 1);
			builder
				.addParticle(rriParticle, Spin.DOWN)
				.addParticle(uniqueParticle, Spin.UP);
		}
		ParticleGroup particleGroup = builder.build();
		Atom atom = new Atom();
		atom.addParticleGroup(particleGroup);
		try {
			atom.sign(keyPair, hasher);
			return ClientAtom.convertFromApiAtom(atom, hasher);
		} catch (AtomAlreadySignedException e) {
			throw new RuntimeException();
		}
	}

	private static Command createCommand(ECKeyPair keyPair, Hasher hasher, int nonce, int numParticles) {
		ClientAtom atom = createAtom(keyPair, hasher, 0, numParticles);
		final byte[] payload = DefaultSerialization.getInstance().toDson(atom, DsonOutput.Output.ALL);
		return new Command(payload);
	}

	private static Command createCommand(ECKeyPair keyPair, Hasher hasher) {
	    ClientAtom atom = createAtom(keyPair, hasher, 0, 1);
		final byte[] payload = DefaultSerialization.getInstance().toDson(atom, DsonOutput.Output.ALL);
		return new Command(payload);
	}

	private static Command createCommand(ECKeyPair keyPair, Hasher hasher, int nonce) {
		ClientAtom atom = createAtom(keyPair, hasher, nonce, 1);
		final byte[] payload = DefaultSerialization.getInstance().toDson(atom, DsonOutput.Output.ALL);
		return new Command(payload);
	}

	@Test
	public void add_command_to_mempool() {
		// Arrange
		Injector injector = getInjector(ecKeyPair);
		Hasher hasher = injector.getInstance(Hasher.class);
		DeterministicMempoolProcessor processor = injector.getInstance(DeterministicMempoolProcessor.class);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		Command command = createCommand(keyPair, hasher);

		// Act
		MempoolAddSuccess mempoolAddSuccess = MempoolAddSuccess.create(command);
		processor.handleMessage(BFTNode.random(), mempoolAddSuccess);

		// Assert
		SystemCounters systemCounters = injector.getInstance(SystemCounters.class);
		assertThat(systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT)).isEqualTo(1);
	}

	@Test
	public void add_same_command_to_mempool() {
		// Arrange
		Injector injector = getInjector(ecKeyPair);
		Hasher hasher = injector.getInstance(Hasher.class);
		DeterministicMempoolProcessor processor = injector.getInstance(DeterministicMempoolProcessor.class);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		Command command = createCommand(keyPair, hasher);
		MempoolAddSuccess mempoolAddSuccess = MempoolAddSuccess.create(command);
		processor.handleMessage(BFTNode.random(), mempoolAddSuccess);

		// Act
		processor.handleMessage(BFTNode.random(), mempoolAddSuccess);

		// Assert
		SystemCounters systemCounters = injector.getInstance(SystemCounters.class);
		assertThat(systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT)).isEqualTo(1);
	}

	@Test
	public void add_conflicting_commands_to_mempool() {
		// Arrange
		Injector injector = getInjector(ecKeyPair);
		Hasher hasher = injector.getInstance(Hasher.class);
		DeterministicMempoolProcessor processor = injector.getInstance(DeterministicMempoolProcessor.class);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		Command command = createCommand(keyPair, hasher, 0, 2);
		MempoolAddSuccess mempoolAddSuccess = MempoolAddSuccess.create(command);
		processor.handleMessage(BFTNode.random(), mempoolAddSuccess);

		// Act
		Command command2 = createCommand(keyPair, hasher, 0, 1);
		MempoolAddSuccess mempoolAddSuccess2 = MempoolAddSuccess.create(command2);
		processor.handleMessage(BFTNode.random(), mempoolAddSuccess2);

		// Assert
		SystemCounters systemCounters = injector.getInstance(SystemCounters.class);
		assertThat(systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT)).isEqualTo(2);
	}

	@Test
	public void add_bad_command_to_mempool() {
		// Arrange
		Injector injector = getInjector(ecKeyPair);
		DeterministicMempoolProcessor processor = injector.getInstance(DeterministicMempoolProcessor.class);
		final Command command = new Command(new byte[0]);

		// Act
		MempoolAddSuccess mempoolAddSuccess = MempoolAddSuccess.create(command);
		processor.handleMessage(BFTNode.random(), mempoolAddSuccess);

		// Assert
		SystemCounters systemCounters = injector.getInstance(SystemCounters.class);
		assertThat(systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT)).isEqualTo(0);
	}

	@Test
	public void missing_dependency_to_mempool() {
		// Arrange
		Injector injector = getInjector(ecKeyPair);
		Hasher hasher = injector.getInstance(Hasher.class);
		DeterministicMempoolProcessor processor = injector.getInstance(DeterministicMempoolProcessor.class);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		Command command = createCommand(keyPair, hasher, 1);

		// Act
		MempoolAddSuccess mempoolAddSuccess = MempoolAddSuccess.create(command);
		processor.handleMessage(BFTNode.random(), mempoolAddSuccess);

		// Assert
		SystemCounters systemCounters = injector.getInstance(SystemCounters.class);
		assertThat(systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT)).isEqualTo(1);
	}

	@Test
	public void replay_command_to_mempool() {
		// Arrange
		Injector injector = getInjector(ecKeyPair);
		Hasher hasher = injector.getInstance(Hasher.class);
		DeterministicMempoolProcessor processor = injector.getInstance(DeterministicMempoolProcessor.class);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		Command command = createCommand(keyPair, hasher);
		var radixEngineStateComputer = injector.getInstance(Key.get(new TypeLiteral<RadixEngineStateComputer>() { }));
		var proof = mock(VerifiedLedgerHeaderAndProof.class);
		when(proof.getAccumulatorState()).thenReturn(new AccumulatorState(1, HashUtils.random256()));
		var commandsAndProof = new VerifiedCommandsAndProof(ImmutableList.of(command), proof);
		radixEngineStateComputer.commit(commandsAndProof, null);

		// Act
		MempoolAddSuccess mempoolAddSuccess = MempoolAddSuccess.create(command);
		processor.handleMessage(BFTNode.random(), mempoolAddSuccess);

		// Assert
		SystemCounters systemCounters = injector.getInstance(SystemCounters.class);
		assertThat(systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT)).isEqualTo(0);
	}

	@Test
	public void mempool_removes_conflicts_on_commit() {
		// Arrange
		Injector injector = getInjector(ecKeyPair);
		Hasher hasher = injector.getInstance(Hasher.class);
		DeterministicMempoolProcessor processor = injector.getInstance(DeterministicMempoolProcessor.class);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		Command command = createCommand(keyPair, hasher, 0, 2);
		MempoolAddSuccess mempoolAddSuccess = MempoolAddSuccess.create(command);
		processor.handleMessage(BFTNode.random(), mempoolAddSuccess);

		// Act
		Command command2 = createCommand(keyPair, hasher, 0, 1);
		var radixEngineStateComputer = injector.getInstance(Key.get(new TypeLiteral<RadixEngineStateComputer>() { }));
		var proof = mock(VerifiedLedgerHeaderAndProof.class);
		when(proof.getAccumulatorState()).thenReturn(new AccumulatorState(1, HashUtils.random256()));
		var commandsAndProof = new VerifiedCommandsAndProof(ImmutableList.of(command2), proof);
		radixEngineStateComputer.commit(commandsAndProof, null);

		// Assert
		SystemCounters systemCounters = injector.getInstance(SystemCounters.class);
		assertThat(systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT)).isEqualTo(0);
	}
}
