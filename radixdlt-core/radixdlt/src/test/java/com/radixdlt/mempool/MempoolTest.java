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

import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atom.Txn;
import com.radixdlt.atommodel.unique.state.UniqueParticle;
import com.radixdlt.atomos.UnclaimedREAddr;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.constraintmachine.SubstateSerialization;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.environment.deterministic.DeterministicProcessor;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.qualifier.NumPeers;
import com.radixdlt.statecomputer.RadixEngineConfig;
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseLocation;

import java.nio.charset.StandardCharsets;
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
	@Inject private SubstateSerialization serialization;
	@Inject @MempoolRelayInitialDelay private long initialDelay;
	@Inject @MempoolRelayRepeatDelay private long repeatDelay;

	private Injector getInjector() {
		return Guice.createInjector(
			new RadixEngineForksLatestOnlyModule(new RERulesConfig(false, 100)),
			MempoolConfig.asModule(10, 10, 200, 500, 10),
			new ForksModule(),
			RadixEngineConfig.asModule(1, 100, 50),
			new SingleNodeAndPeersDeterministicNetworkModule(),
			new MockedGenesisModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bindConstant().annotatedWith(NumPeers.class).to(NUM_PEERS);
					bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
				}
			}
		);
	}

	private BFTNode getFirstPeer() {
		return peersView.peers().findFirst().get().bftNode();
	}

	private Txn createTxn(ECKeyPair keyPair, int numParticles) {
		TxLowLevelBuilder atomBuilder = TxLowLevelBuilder.newBuilder(serialization);
		for (int i = 0; i < numParticles; i++) {
			var symbol = "test" + (char) ('c' + i);
			var addr = REAddr.ofHashedKey(keyPair.getPublicKey(), symbol);
			var rriParticle = new UnclaimedREAddr(addr);
			var uniqueParticle = new UniqueParticle(addr);
			atomBuilder
				.virtualDown(rriParticle, symbol.getBytes(StandardCharsets.UTF_8))
				.up(uniqueParticle)
				.end();
		}
		var signature = keyPair.sign(atomBuilder.hashToSign());
		return atomBuilder.sig(signature).build();
	}

	private Txn createTxn(ECKeyPair keyPair) {
		return createTxn(keyPair, 1);
	}

	@Test
	public void add_local_command_to_mempool() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		var txn = createTxn(keyPair);

		// Act
		processor.handleMessage(self, MempoolAdd.create(txn), null);

		// Assert
		assertThat(systemCounters.get(CounterType.MEMPOOL_COUNT)).isEqualTo(1);
		// FIXME: Added hack which requires genesis to be sent as message so ignore this check for now
		//assertThat(network.allMessages())
			//.hasOnlyOneElementSatisfying(m -> assertThat(m.message()).isInstanceOf(MempoolAddSuccess.class));
	}

	@Test
	public void add_remote_command_to_mempool() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		var txn = createTxn(keyPair);

		// Act
		processor.handleMessage(getFirstPeer(), MempoolAdd.create(txn), null);

		// Assert
		assertThat(systemCounters.get(CounterType.MEMPOOL_COUNT)).isEqualTo(1);
		// FIXME: Added hack which requires genesis to be sent as message so ignore this check for now
		//assertThat(network.allMessages())
			//.hasOnlyOneElementSatisfying(m -> assertThat(m.message()).isInstanceOf(MempoolAddSuccess.class));
	}

	@Test
	public void relay_successful_local_add() {
		// Arrange
		getInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		var txn = createTxn(keyPair);

		// Act
		processor.handleMessage(self, MempoolAddSuccess.create(txn), null);

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
		processor.handleMessage(self, MempoolAddSuccess.create(txn, getFirstPeer()), null);

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
		processor.handleMessage(getFirstPeer(), mempoolAdd, null);

		// Act
		processor.handleMessage(getFirstPeer(), mempoolAdd, null);

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
		processor.handleMessage(getFirstPeer(), mempoolAdd, null);

		// Act
		var txn2 = createTxn(keyPair, 1);
		MempoolAdd mempoolAddSuccess2 = MempoolAdd.create(txn2);
		processor.handleMessage(getFirstPeer(), mempoolAddSuccess2, null);

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
		processor.handleMessage(getFirstPeer(), mempoolAdd, null);

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
		processor.handleMessage(getFirstPeer(), mempoolAdd, null);

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
		processor.handleMessage(getFirstPeer(), mempoolAdd, null);

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
		processor.handleMessage(getFirstPeer(), mempoolAdd, null);
		var txn2 = createTxn(keyPair, 3);
		processor.handleMessage(getFirstPeer(), MempoolAdd.create(txn2), null);

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
	@Ignore("Added hack which requires genesis to be sent as message. Reenable when fixed.")
	public void mempool_should_relay_commands_respecting_delay_config_params() throws Exception {
		// Arrange
		getInjector().injectMembers(this);
		final var keyPair = ECKeyPair.generateNew();
		final var txn = createTxn(keyPair);
		final var mempoolAdd = MempoolAdd.create(txn);
		processor.handleMessage(self, mempoolAdd, null);
		assertThat(systemCounters.get(CounterType.MEMPOOL_COUNT)).isEqualTo(1);

		assertThat(network.allMessages())
			.hasOnlyOneElementSatisfying(m -> assertThat(m.message()).isInstanceOf(MempoolAddSuccess.class));
		network.dropMessages(msg -> msg.message() instanceof MempoolAddSuccess);

		// should not relay immediately
		processor.handleMessage(self, MempoolRelayTrigger.create(), null);
		assertThat(network.allMessages()).isEmpty();

		// should relay after initial delay
		Thread.sleep(initialDelay);
		processor.handleMessage(self, MempoolRelayTrigger.create(), null);
		assertThat(network.allMessages())
			.extracting(ControlledMessage::message)
			.hasOnlyElementsOfType(MempoolAdd.class);
		network.dropMessages(msg -> msg.message() instanceof MempoolAdd);

		// should not relay again immediately
		processor.handleMessage(self, MempoolRelayTrigger.create(), null);
		assertThat(network.allMessages()).isEmpty();

		// should relay after repeat delay
		Thread.sleep(repeatDelay);
		processor.handleMessage(self, MempoolRelayTrigger.create(), null);
		assertThat(network.allMessages())
			.extracting(ControlledMessage::message)
			.hasOnlyElementsOfType(MempoolAdd.class);
	}
}
