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
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.MempoolRelayerModule;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.atommodel.unique.UniqueParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.deterministic.DeterministicMempoolProcessor;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.store.DatabaseLocation;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MempoolTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject private Hasher hasher;
	@Inject private DeterministicMempoolProcessor processor;
	@Inject private RadixEngineStateComputer stateComputer;
	@Inject private SystemCounters systemCounters;

	private Injector getInjector() {
		return Guice.createInjector(
			new SingleNodeAndPeersDeterministicNetworkModule(),
			new MempoolRelayerModule(),
				new AbstractModule() {
				@Override
				protected void configure() {
					bindConstant().annotatedWith(Names.named("numPeers")).to(0);
					AddressBook addressBook = mock(AddressBook.class);
					bind(AddressBook.class).toInstance(addressBook);
					bindConstant().annotatedWith(MempoolMaxSize.class).to(10);
					bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(View.of(100L));
					bindConstant().annotatedWith(DatabaseLocation.class)
						.to(folder.getRoot().getAbsolutePath());
				}
			}
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
		atom.sign(keyPair, hasher);
		return ClientAtom.convertFromApiAtom(atom, hasher);
	}

	private static Command createCommand(ECKeyPair keyPair, Hasher hasher, int nonce, int numParticles) {
		ClientAtom atom = createAtom(keyPair, hasher, nonce, numParticles);
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
	public void add_remote_command_to_mempool() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		Command command = createCommand(keyPair, hasher);

		// Act
		MempoolAddSuccess mempoolAddSuccess = MempoolAddSuccess.create(command);
		processor.handleMessage(BFTNode.random(), mempoolAddSuccess);

		// Assert
		assertThat(systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT)).isEqualTo(1);
	}

	@Test
	public void add_same_command_to_mempool() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		Command command = createCommand(keyPair, hasher);
		MempoolAddSuccess mempoolAddSuccess = MempoolAddSuccess.create(command);
		processor.handleMessage(BFTNode.random(), mempoolAddSuccess);

		// Act
		processor.handleMessage(BFTNode.random(), mempoolAddSuccess);

		// Assert
		assertThat(systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT)).isEqualTo(1);
	}

	@Test
	public void add_conflicting_commands_to_mempool() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		Command command = createCommand(keyPair, hasher, 0, 2);
		MempoolAddSuccess mempoolAddSuccess = MempoolAddSuccess.create(command);
		processor.handleMessage(BFTNode.random(), mempoolAddSuccess);

		// Act
		Command command2 = createCommand(keyPair, hasher, 0, 1);
		MempoolAddSuccess mempoolAddSuccess2 = MempoolAddSuccess.create(command2);
		processor.handleMessage(BFTNode.random(), mempoolAddSuccess2);

		// Assert
		assertThat(systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT)).isEqualTo(2);
	}

	@Test
	public void add_bad_command_to_mempool() {
		// Arrange
		getInjector().injectMembers(this);
		final Command command = new Command(new byte[0]);

		// Act
		MempoolAddSuccess mempoolAddSuccess = MempoolAddSuccess.create(command);
		processor.handleMessage(BFTNode.random(), mempoolAddSuccess);

		// Assert
		assertThat(systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT)).isEqualTo(0);
	}

	@Test
	public void missing_dependency_to_mempool() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		Command command = createCommand(keyPair, hasher, 1);

		// Act
		MempoolAddSuccess mempoolAddSuccess = MempoolAddSuccess.create(command);
		processor.handleMessage(BFTNode.random(), mempoolAddSuccess);

		// Assert
		assertThat(systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT)).isEqualTo(1);
	}

	@Test
	public void replay_command_to_mempool() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		Command command = createCommand(keyPair, hasher);
		var proof = mock(VerifiedLedgerHeaderAndProof.class);
		when(proof.getAccumulatorState()).thenReturn(new AccumulatorState(1, HashUtils.random256()));
		var commandsAndProof = new VerifiedCommandsAndProof(ImmutableList.of(command), proof);
		stateComputer.commit(commandsAndProof, null);

		// Act
		MempoolAddSuccess mempoolAddSuccess = MempoolAddSuccess.create(command);
		processor.handleMessage(BFTNode.random(), mempoolAddSuccess);

		// Assert
		assertThat(systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT)).isEqualTo(0);
	}

	@Test
	public void mempool_removes_conflicts_on_commit() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		Command command = createCommand(keyPair, hasher, 0, 2);
		MempoolAddSuccess mempoolAddSuccess = MempoolAddSuccess.create(command);
		processor.handleMessage(BFTNode.random(), mempoolAddSuccess);

		// Act
		Command command2 = createCommand(keyPair, hasher, 0, 1);
		var proof = mock(VerifiedLedgerHeaderAndProof.class);
		when(proof.getAccumulatorState()).thenReturn(new AccumulatorState(1, HashUtils.random256()));
		var commandsAndProof = new VerifiedCommandsAndProof(ImmutableList.of(command2), proof);
		stateComputer.commit(commandsAndProof, null);

		// Assert
		assertThat(systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT)).isEqualTo(0);
	}

	@Test
	public void mempool_removes_multiple_conflicts_on_commit() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		Command command = createCommand(keyPair, hasher, 0, 2);
		MempoolAddSuccess mempoolAddSuccess = MempoolAddSuccess.create(command);
		processor.handleMessage(BFTNode.random(), mempoolAddSuccess);
		Command command2 = createCommand(keyPair, hasher, 0, 3);
		processor.handleMessage(BFTNode.random(), MempoolAddSuccess.create(command2));

		// Act
		Command command3 = createCommand(keyPair, hasher, 0, 1);
		var proof = mock(VerifiedLedgerHeaderAndProof.class);
		when(proof.getAccumulatorState()).thenReturn(new AccumulatorState(1, HashUtils.random256()));
		var commandsAndProof = new VerifiedCommandsAndProof(ImmutableList.of(command3), proof);
		stateComputer.commit(commandsAndProof, null);

		// Assert
		assertThat(systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT)).isEqualTo(0);
	}
}
