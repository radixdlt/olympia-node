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
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atommodel.unique.UniqueParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.deterministic.DeterministicProcessor;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.atom.Atom;
import com.radixdlt.network.addressbook.PeersView;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisAtomModule;
import com.radixdlt.store.DatabaseLocation;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MempoolTest {
	private static final int NUM_PEERS = 2;

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject @Self private BFTNode self;
	@Inject @Genesis private List<Atom> genesisAtoms;
	@Inject private Hasher hasher;
	@Inject private DeterministicProcessor processor;
	@Inject private DeterministicNetwork network;
	@Inject private RadixEngineStateComputer stateComputer;
	@Inject private SystemCounters systemCounters;
	@Inject private PeersView peersView;
	@Inject private MempoolConfig mempoolConfig;

	private Injector getInjector() {
		return Guice.createInjector(
			new SingleNodeAndPeersDeterministicNetworkModule(),
			new MockedGenesisAtomModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bindConstant().annotatedWith(Names.named("numPeers")).to(NUM_PEERS);
					bind(MempoolConfig.class).toInstance(MempoolConfig.of(10L, 10L, 200L, 500L, 10));
					bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(View.of(100L));
					bindConstant().annotatedWith(DatabaseLocation.class)
						.to(folder.getRoot().getAbsolutePath());
				}
			}
		);
	}

	private BFTNode getFirstPeer() {
		return peersView.peers().get(0);
	}

	private static Atom createAtom(ECKeyPair keyPair, int numParticles) {
		RadixAddress address = new RadixAddress((byte) 0, keyPair.getPublicKey());

		TxLowLevelBuilder atomBuilder = TxLowLevelBuilder.newBuilder();
		for (int i = 0; i < numParticles; i++) {
			RRI rri = RRI.of(address, "test" + i);
			RRIParticle rriParticle = new RRIParticle(rri);
			UniqueParticle uniqueParticle = new UniqueParticle("test" + i, address);
			atomBuilder
				.virtualDown(rriParticle)
				.up(uniqueParticle);
		}
		atomBuilder.particleGroup();
		return atomBuilder.signAndBuild(keyPair::sign);
	}

	private static Command createCommand(ECKeyPair keyPair, int numParticles) {
		Atom atom = createAtom(keyPair, numParticles);
		final byte[] payload = DefaultSerialization.getInstance().toDson(atom, DsonOutput.Output.ALL);
		return new Command(payload);
	}

	private static Command createCommand(ECKeyPair keyPair) {
		Atom atom = createAtom(keyPair, 1);
		final byte[] payload = DefaultSerialization.getInstance().toDson(atom, DsonOutput.Output.ALL);
		return new Command(payload);
	}

	@Test
	public void add_local_command_to_mempool() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		Command command = createCommand(keyPair);

		// Act
		processor.handleMessage(self, MempoolAdd.create(command));

		// Assert
		assertThat(systemCounters.get(CounterType.MEMPOOL_COUNT)).isEqualTo(1);
		assertThat(network.allMessages())
			.hasOnlyOneElementSatisfying(m -> assertThat(m.message()).isInstanceOf(MempoolAddSuccess.class));
	}

	@Test
	public void add_remote_command_to_mempool() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		Command command = createCommand(keyPair);

		// Act
		processor.handleMessage(getFirstPeer(), MempoolAdd.create(command));

		// Assert
		assertThat(systemCounters.get(CounterType.MEMPOOL_COUNT)).isEqualTo(1);
		assertThat(network.allMessages())
			.hasOnlyOneElementSatisfying(m -> assertThat(m.message()).isInstanceOf(MempoolAddSuccess.class));
	}

	@Test
	public void relay_successful_local_add() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		Command command = createCommand(keyPair);

		// Act
		processor.handleMessage(self, MempoolAddSuccess.create(command));

		// Assert
		assertThat(systemCounters.get(CounterType.MEMPOOL_RELAYER_SENT_COUNT)).isEqualTo(NUM_PEERS);
	}

	@Test
	public void relay_successful_remote_add() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		Command command = createCommand(keyPair);

		// Act
		processor.handleMessage(self, MempoolAddSuccess.create(command, getFirstPeer()));

		// Assert
		assertThat(systemCounters.get(CounterType.MEMPOOL_RELAYER_SENT_COUNT)).isEqualTo(NUM_PEERS - 1);
	}

	@Test
	public void add_same_command_to_mempool() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		Command command = createCommand(keyPair);
		MempoolAdd mempoolAdd = MempoolAdd.create(command);
		processor.handleMessage(getFirstPeer(), mempoolAdd);

		// Act
		processor.handleMessage(getFirstPeer(), mempoolAdd);

		// Assert
		assertThat(systemCounters.get(CounterType.MEMPOOL_COUNT)).isEqualTo(1);
	}

	@Test
	public void add_conflicting_commands_to_mempool() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		Command command = createCommand(keyPair, 2);
		MempoolAdd mempoolAdd = MempoolAdd.create(command);
		processor.handleMessage(getFirstPeer(), mempoolAdd);

		// Act
		Command command2 = createCommand(keyPair, 1);
		MempoolAdd mempoolAddSuccess2 = MempoolAdd.create(command2);
		processor.handleMessage(getFirstPeer(), mempoolAddSuccess2);

		// Assert
		assertThat(systemCounters.get(CounterType.MEMPOOL_COUNT)).isEqualTo(2);
	}

	@Test
	public void add_bad_command_to_mempool() {
		// Arrange
		getInjector().injectMembers(this);
		final Command command = new Command(new byte[0]);

		// Act
		MempoolAdd mempoolAdd = MempoolAdd.create(command);
		processor.handleMessage(getFirstPeer(), mempoolAdd);

		// Assert
		assertThat(systemCounters.get(CounterType.MEMPOOL_COUNT)).isEqualTo(0);
	}

	@Test
	public void replay_command_to_mempool() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		Command command = createCommand(keyPair);
		var proof = mock(LedgerProof.class);
		when(proof.getAccumulatorState()).thenReturn(new AccumulatorState(genesisAtoms.size(), HashUtils.random256()));
		when(proof.getStateVersion()).thenReturn((long) genesisAtoms.size());
		var commandsAndProof = new VerifiedCommandsAndProof(ImmutableList.of(command), proof);
		stateComputer.commit(commandsAndProof, null);

		// Act
		MempoolAdd mempoolAdd = MempoolAdd.create(command);
		processor.handleMessage(getFirstPeer(), mempoolAdd);

		// Assert
		assertThat(systemCounters.get(CounterType.MEMPOOL_COUNT)).isEqualTo(0);
	}

	@Test
	public void mempool_removes_conflicts_on_commit() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		Command command = createCommand(keyPair, 2);
		MempoolAdd mempoolAdd = MempoolAdd.create(command);
		processor.handleMessage(getFirstPeer(), mempoolAdd);

		// Act
		Command command2 = createCommand(keyPair, 1);
		var proof = mock(LedgerProof.class);
		when(proof.getAccumulatorState()).thenReturn(new AccumulatorState(genesisAtoms.size(), HashUtils.random256()));
		when(proof.getStateVersion()).thenReturn((long) genesisAtoms.size());
		var commandsAndProof = new VerifiedCommandsAndProof(ImmutableList.of(command2), proof);
		stateComputer.commit(commandsAndProof, null);

		// Assert
		assertThat(systemCounters.get(CounterType.MEMPOOL_COUNT)).isEqualTo(0);
	}

	@Test
	public void mempool_removes_multiple_conflicts_on_commit() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		Command command = createCommand(keyPair, 2);
		MempoolAdd mempoolAdd = MempoolAdd.create(command);
		processor.handleMessage(getFirstPeer(), mempoolAdd);
		Command command2 = createCommand(keyPair, 3);
		processor.handleMessage(getFirstPeer(), MempoolAdd.create(command2));

		// Act
		Command command3 = createCommand(keyPair, 1);
		var proof = mock(LedgerProof.class);
		when(proof.getAccumulatorState()).thenReturn(new AccumulatorState(genesisAtoms.size(), HashUtils.random256()));
		when(proof.getStateVersion()).thenReturn((long) genesisAtoms.size());
		var commandsAndProof = new VerifiedCommandsAndProof(ImmutableList.of(command3), proof);
		stateComputer.commit(commandsAndProof, null);

		// Assert
		assertThat(systemCounters.get(CounterType.MEMPOOL_COUNT)).isEqualTo(0);
	}

	@Test
	public void mempool_should_relay_commands_respecting_delay_config_params() throws Exception {
		// Arrange
		getInjector().injectMembers(this);
		final var keyPair = ECKeyPair.generateNew();
		final var command = createCommand(keyPair);
		final var mempoolAdd = MempoolAdd.create(command);
		processor.handleMessage(self, mempoolAdd);
		assertThat(systemCounters.get(CounterType.MEMPOOL_COUNT)).isEqualTo(1);

		assertThat(network.allMessages())
			.hasOnlyOneElementSatisfying(m -> assertThat(m.message()).isInstanceOf(MempoolAddSuccess.class));
		network.dropMessages(msg -> msg.message() instanceof MempoolAddSuccess);

		// should not relay immediately
		processor.handleMessage(self, MempoolRelayTrigger.create());
		assertThat(network.allMessages()).isEmpty();

		// should relay after initial delay
		Thread.sleep(mempoolConfig.commandRelayInitialDelay());
		processor.handleMessage(self, MempoolRelayTrigger.create());
		assertThat(network.allMessages())
			.hasOnlyOneElementSatisfying(m -> assertThat(m.message()).isInstanceOf(MempoolRelayCommands.class));
		network.dropMessages(msg -> msg.message() instanceof MempoolRelayCommands);

		// should not relay again immediately
		processor.handleMessage(self, MempoolRelayTrigger.create());
		assertThat(network.allMessages()).isEmpty();

		// should relay after repeat delay
		Thread.sleep(mempoolConfig.commandRelayRepeatDelay());
		processor.handleMessage(self, MempoolRelayTrigger.create());
		assertThat(network.allMessages())
			.hasOnlyOneElementSatisfying(m -> assertThat(m.message()).isInstanceOf(MempoolRelayCommands.class));
	}
}
