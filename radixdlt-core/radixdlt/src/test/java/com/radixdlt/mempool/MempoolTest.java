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

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atom.Txn;
import com.radixdlt.atommodel.unique.UniqueParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.atomos.RriId;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.environment.deterministic.DeterministicProcessor;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.network.addressbook.PeersView;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
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
	@Inject @Genesis private VerifiedTxnsAndProof genesisTxns;
	@Inject private DeterministicProcessor processor;
	@Inject private DeterministicNetwork network;
	@Inject private RadixEngineStateComputer stateComputer;
	@Inject private SystemCounters systemCounters;
	@Inject private PeersView peersView;
	@Inject private MempoolConfig mempoolConfig;

	private Injector getInjector() {
		return Guice.createInjector(
			new SingleNodeAndPeersDeterministicNetworkModule(),
			new MockedGenesisModule(),
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

	private static Txn createTxn(ECKeyPair keyPair, int numParticles) {
		TxLowLevelBuilder atomBuilder = TxLowLevelBuilder.newBuilder();
		for (int i = 0; i < numParticles; i++) {
			var rri = RRI.of(keyPair.getPublicKey(), "test" + (char) ('c' + i));
			var rriParticle = new RRIParticle(rri);
			var rriId = RriId.fromRri(rri);
			UniqueParticle uniqueParticle = new UniqueParticle(rriId);
			atomBuilder
				.virtualDown(rriParticle)
				.up(uniqueParticle)
				.particleGroup();
		}
		var signature = keyPair.sign(atomBuilder.hashToSign());
		return atomBuilder.sig(signature).build();
	}

	private static Txn createTxn(ECKeyPair keyPair) {
		return createTxn(keyPair, 1);
	}

	@Test
	public void add_local_command_to_mempool() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		var txn = createTxn(keyPair);

		// Act
		processor.handleMessage(self, MempoolAdd.create(txn));

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
		var txn = createTxn(keyPair);

		// Act
		processor.handleMessage(getFirstPeer(), MempoolAdd.create(txn));

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
		var txn = createTxn(keyPair);

		// Act
		processor.handleMessage(self, MempoolAddSuccess.create(txn));

		// Assert
		assertThat(systemCounters.get(CounterType.MEMPOOL_RELAYER_SENT_COUNT)).isEqualTo(NUM_PEERS);
	}

	@Test
	public void relay_successful_remote_add() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		var txn = createTxn(keyPair);

		// Act
		processor.handleMessage(self, MempoolAddSuccess.create(txn, getFirstPeer()));

		// Assert
		assertThat(systemCounters.get(CounterType.MEMPOOL_RELAYER_SENT_COUNT)).isEqualTo(NUM_PEERS - 1);
	}

	@Test
	public void add_same_command_to_mempool() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		var txn = createTxn(keyPair);
		MempoolAdd mempoolAdd = MempoolAdd.create(txn);
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
		var txn = createTxn(keyPair, 2);
		MempoolAdd mempoolAdd = MempoolAdd.create(txn);
		processor.handleMessage(getFirstPeer(), mempoolAdd);

		// Act
		var txn2 = createTxn(keyPair, 1);
		MempoolAdd mempoolAddSuccess2 = MempoolAdd.create(txn2);
		processor.handleMessage(getFirstPeer(), mempoolAddSuccess2);

		// Assert
		assertThat(systemCounters.get(CounterType.MEMPOOL_COUNT)).isEqualTo(2);
	}

	@Test
	public void add_bad_command_to_mempool() {
		// Arrange
		getInjector().injectMembers(this);
		final var txn = Txn.create(new byte[0]);

		// Act
		MempoolAdd mempoolAdd = MempoolAdd.create(txn);
		processor.handleMessage(getFirstPeer(), mempoolAdd);

		// Assert
		assertThat(systemCounters.get(CounterType.MEMPOOL_COUNT)).isEqualTo(0);
	}

	@Test
	public void replay_command_to_mempool() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		var txn = createTxn(keyPair);
		var proof = mock(LedgerProof.class);
		when(proof.getAccumulatorState()).thenReturn(new AccumulatorState(genesisTxns.getTxns().size(), HashUtils.random256()));
		when(proof.getStateVersion()).thenReturn((long) genesisTxns.getTxns().size());
		var commandsAndProof = VerifiedTxnsAndProof.create(List.of(txn), proof);
		stateComputer.commit(commandsAndProof, null);

		// Act
		MempoolAdd mempoolAdd = MempoolAdd.create(txn);
		processor.handleMessage(getFirstPeer(), mempoolAdd);

		// Assert
		assertThat(systemCounters.get(CounterType.MEMPOOL_COUNT)).isEqualTo(0);
	}

	@Test
	public void mempool_removes_conflicts_on_commit() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		var txn = createTxn(keyPair, 2);
		MempoolAdd mempoolAdd = MempoolAdd.create(txn);
		processor.handleMessage(getFirstPeer(), mempoolAdd);

		// Act
		var txn2 = createTxn(keyPair, 1);
		var proof = mock(LedgerProof.class);
		when(proof.getAccumulatorState()).thenReturn(new AccumulatorState(genesisTxns.getTxns().size(), HashUtils.random256()));
		when(proof.getStateVersion()).thenReturn((long) genesisTxns.getTxns().size());
		var commandsAndProof = VerifiedTxnsAndProof.create(List.of(txn2), proof);
		stateComputer.commit(commandsAndProof, null);

		// Assert
		assertThat(systemCounters.get(CounterType.MEMPOOL_COUNT)).isEqualTo(0);
	}

	@Test
	public void mempool_removes_multiple_conflicts_on_commit() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		var txn = createTxn(keyPair, 2);
		MempoolAdd mempoolAdd = MempoolAdd.create(txn);
		processor.handleMessage(getFirstPeer(), mempoolAdd);
		var txn2 = createTxn(keyPair, 3);
		processor.handleMessage(getFirstPeer(), MempoolAdd.create(txn2));

		// Act
		var txn3 = createTxn(keyPair, 1);
		var proof = mock(LedgerProof.class);
		when(proof.getAccumulatorState()).thenReturn(new AccumulatorState(genesisTxns.getTxns().size(), HashUtils.random256()));
		when(proof.getStateVersion()).thenReturn((long) genesisTxns.getTxns().size());
		var commandsAndProof = VerifiedTxnsAndProof.create(List.of(txn3), proof);
		stateComputer.commit(commandsAndProof, null);

		// Assert
		assertThat(systemCounters.get(CounterType.MEMPOOL_COUNT)).isEqualTo(0);
	}

	@Test
	public void mempool_should_relay_commands_respecting_delay_config_params() throws Exception {
		// Arrange
		getInjector().injectMembers(this);
		final var keyPair = ECKeyPair.generateNew();
		final var txn = createTxn(keyPair);
		final var mempoolAdd = MempoolAdd.create(txn);
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
			.extracting(ControlledMessage::message)
			.hasOnlyElementsOfType(MempoolAdd.class);
		network.dropMessages(msg -> msg.message() instanceof MempoolAdd);

		// should not relay again immediately
		processor.handleMessage(self, MempoolRelayTrigger.create());
		assertThat(network.allMessages()).isEmpty();

		// should relay after repeat delay
		Thread.sleep(mempoolConfig.commandRelayRepeatDelay());
		processor.handleMessage(self, MempoolRelayTrigger.create());
		assertThat(network.allMessages())
			.extracting(ControlledMessage::message)
			.hasOnlyElementsOfType(MempoolAdd.class);
	}
}
