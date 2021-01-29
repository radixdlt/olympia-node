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
import com.radixdlt.MVStorePersistenceModule;
import com.radixdlt.MempoolRelayerModule;
import com.radixdlt.NoFeeModule;
import com.radixdlt.RadixEngineModule;
import com.radixdlt.RadixEngineStoreModule;
import com.radixdlt.RadixEngineValidatorComputersModule;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.atommodel.AtomAlreadySignedException;
import com.radixdlt.atommodel.unique.UniqueParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.HashSigner;
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
import com.radixdlt.ledger.StateComputerLedger.LedgerUpdateSender;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.MaxValidators;
import com.radixdlt.statecomputer.MinValidators;
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
			new LedgerLocalMempoolModule(1),

			// State Computer
			new RadixEngineModule(),
			new RadixEngineStoreModule(),
			new RadixEngineValidatorComputersModule(),

			// Fees
			new NoFeeModule(),

			new MVStorePersistenceModule(),
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

	@Test
	public void add_command_to_mempool() {
		// Arrange
		Injector injector = getInjector(ecKeyPair);
		Hasher hasher = injector.getInstance(Hasher.class);
		DeterministicMempoolProcessor processor = injector.getInstance(DeterministicMempoolProcessor.class);

		ECKeyPair keyPair = ECKeyPair.generateNew();
		RadixAddress address = new RadixAddress((byte) 0, keyPair.getPublicKey());

		RRI rri = RRI.of(address, "test");
		RRIParticle rriParticle = new RRIParticle(rri, 0);
		UniqueParticle uniqueParticle = new UniqueParticle("test", address, 1);
		ParticleGroup particleGroup = ParticleGroup.builder()
			.addParticle(rriParticle, Spin.DOWN)
			.addParticle(uniqueParticle, Spin.UP)
			.build();
		Atom atom = new Atom();
		atom.addParticleGroup(particleGroup);
		final Command command;
		try {
			atom.sign(keyPair, hasher);
			ClientAtom clientAtom = ClientAtom.convertFromApiAtom(atom, hasher);
			final byte[] payload = DefaultSerialization.getInstance().toDson(clientAtom, DsonOutput.Output.ALL);
			command = new Command(payload);
		} catch (AtomAlreadySignedException e) {
			throw new RuntimeException();
		}

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
		RadixAddress address = new RadixAddress((byte) 0, keyPair.getPublicKey());

		RRI rri = RRI.of(address, "test");
		RRIParticle rriParticle = new RRIParticle(rri, 0);
		UniqueParticle uniqueParticle = new UniqueParticle("test", address, 1);
		ParticleGroup particleGroup = ParticleGroup.builder()
				.addParticle(rriParticle, Spin.DOWN)
				.addParticle(uniqueParticle, Spin.UP)
				.build();
		Atom atom = new Atom();
		atom.addParticleGroup(particleGroup);
		final Command command;
		try {
			atom.sign(keyPair, hasher);
			ClientAtom clientAtom = ClientAtom.convertFromApiAtom(atom, hasher);
			final byte[] payload = DefaultSerialization.getInstance().toDson(clientAtom, DsonOutput.Output.ALL);
			command = new Command(payload);
		} catch (AtomAlreadySignedException e) {
			throw new RuntimeException();
		}
		MempoolAddSuccess mempoolAddSuccess = MempoolAddSuccess.create(command);
		processor.handleMessage(BFTNode.random(), mempoolAddSuccess);

		// Act
		processor.handleMessage(BFTNode.random(), mempoolAddSuccess);

		// Assert
		SystemCounters systemCounters = injector.getInstance(SystemCounters.class);
		assertThat(systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT)).isEqualTo(1);
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
}
