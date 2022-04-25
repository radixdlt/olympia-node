/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.mempool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.radixdlt.modules.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.application.system.scrypt.Syscall;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.monitoring.SystemCounters.CounterType;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.environment.deterministic.DeterministicProcessor;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.CurrentForkView;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.MainnetForksModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.utils.PrivateKeys;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MempoolTest {
  private static final ECKeyPair VALIDATOR_KEY = PrivateKeys.ofNumeric(1);
  private static final int NUM_PEERS = 2;

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Inject @Self private BFTNode self;
  @Inject @Genesis private VerifiedTxnsAndProof genesisTxns;
  @Inject private DeterministicProcessor processor;
  @Inject private DeterministicNetwork network;
  @Inject private RadixEngineStateComputer stateComputer;
  @Inject private SystemCounters systemCounters;
  @Inject private PeersView peersView;
  @Inject private CurrentForkView currentForkView;
  @Inject @MempoolRelayInitialDelay private long initialDelay;
  @Inject @MempoolRelayRepeatDelay private long repeatDelay;

  private Injector getInjector() {
    return Guice.createInjector(
        new MainnetForksModule(),
        new RadixEngineForksLatestOnlyModule(
            RERulesConfig.testingDefault().removeSigsPerRoundLimit()),
        new ForksModule(),
        MempoolConfig.asModule(10, 10, 200, 500, 10),
        new SingleNodeAndPeersDeterministicNetworkModule(VALIDATOR_KEY, NUM_PEERS),
        new MockedGenesisModule(
            Set.of(VALIDATOR_KEY.getPublicKey()), Amount.ofTokens(1000), Amount.ofTokens(100)),
        new AbstractModule() {
          @Override
          protected void configure() {
            bindConstant()
                .annotatedWith(DatabaseLocation.class)
                .to(folder.getRoot().getAbsolutePath());
          }
        });
  }

  private BFTNode getFirstPeer() {
    return peersView.peers().findFirst().get().bftNode();
  }

  private Txn createTxn(ECKeyPair keyPair, int numMutexes) throws Exception {
    final var atomBuilder =
        TxLowLevelBuilder.newBuilder(
            currentForkView.currentForkConfig().engineRules().serialization());
    for (int i = 0; i < numMutexes; i++) {
      var symbol = "test" + (char) ('c' + i);
      var addr = REAddr.ofHashedKey(keyPair.getPublicKey(), symbol);
      atomBuilder
          .syscall(Syscall.READDR_CLAIM, symbol.getBytes(StandardCharsets.UTF_8))
          .virtualDown(
              SubstateId.ofSubstate(genesisTxns.getTxns().get(0).getId(), 0), addr.getBytes())
          .end();
    }
    var signature = keyPair.sign(atomBuilder.hashToSign());
    return atomBuilder.sig(signature).build();
  }

  private Txn createTxn(ECKeyPair keyPair) throws Exception {
    return createTxn(keyPair, 1);
  }

  @Test
  public void add_local_command_to_mempool() throws Exception {
    // Arrange
    getInjector().injectMembers(this);
    ECKeyPair keyPair = ECKeyPair.generateNew();
    var txn = createTxn(keyPair);

    // Act
    processor.handleMessage(self, MempoolAdd.create(txn), null);

    // Assert
    assertThat(systemCounters.get(CounterType.MEMPOOL_CURRENT_SIZE)).isEqualTo(1);
    // FIXME: Added hack which requires genesis to be sent as message so ignore this check for now
    // assertThat(network.allMessages())
    // .hasOnlyOneElementSatisfying(m ->
    // assertThat(m.message()).isInstanceOf(MempoolAddSuccess.class));
  }

  @Test
  public void add_remote_command_to_mempool() throws Exception {
    // Arrange
    getInjector().injectMembers(this);
    ECKeyPair keyPair = ECKeyPair.generateNew();
    var txn = createTxn(keyPair);

    // Act
    processor.handleMessage(getFirstPeer(), MempoolAdd.create(txn), null);

    // Assert
    assertThat(systemCounters.get(CounterType.MEMPOOL_CURRENT_SIZE)).isEqualTo(1);
    // FIXME: Added hack which requires genesis to be sent as message so ignore this check for now
    // assertThat(network.allMessages())
    // .hasOnlyOneElementSatisfying(m ->
    // assertThat(m.message()).isInstanceOf(MempoolAddSuccess.class));
  }

  @Test
  public void relay_successful_local_add() throws Exception {
    // Arrange
    getInjector().injectMembers(this);
    ECKeyPair keyPair = ECKeyPair.generateNew();
    var txn = createTxn(keyPair);

    // Act
    processor.handleMessage(self, MempoolAddSuccess.create(txn, null, null), null);

    // Assert
    assertThat(systemCounters.get(CounterType.MEMPOOL_RELAYS_SENT)).isEqualTo(NUM_PEERS);
  }

  @Test
  public void relay_successful_remote_add() throws Exception {
    // Arrange
    getInjector().injectMembers(this);
    ECKeyPair keyPair = ECKeyPair.generateNew();
    var txn = createTxn(keyPair);

    // Act
    processor.handleMessage(self, MempoolAddSuccess.create(txn, null, getFirstPeer()), null);

    // Assert
    assertThat(systemCounters.get(CounterType.MEMPOOL_RELAYS_SENT)).isEqualTo(NUM_PEERS - 1);
  }

  @Test
  public void add_same_command_to_mempool() throws Exception {
    // Arrange
    getInjector().injectMembers(this);
    ECKeyPair keyPair = ECKeyPair.generateNew();
    var txn = createTxn(keyPair);
    MempoolAdd mempoolAdd = MempoolAdd.create(txn);
    processor.handleMessage(getFirstPeer(), mempoolAdd, null);

    // Act
    processor.handleMessage(getFirstPeer(), mempoolAdd, null);

    // Assert
    assertThat(systemCounters.get(CounterType.MEMPOOL_CURRENT_SIZE)).isEqualTo(1);
  }

  @Test
  public void add_conflicting_commands_to_mempool() throws Exception {
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
    assertThat(systemCounters.get(CounterType.MEMPOOL_CURRENT_SIZE)).isEqualTo(2);
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
    assertThat(systemCounters.get(CounterType.MEMPOOL_CURRENT_SIZE)).isZero();
  }

  @Test
  public void replay_command_to_mempool() throws Exception {
    // Arrange
    getInjector().injectMembers(this);
    ECKeyPair keyPair = ECKeyPair.generateNew();
    var txn = createTxn(keyPair);
    var proof = mock(LedgerProof.class);
    when(proof.getAccumulatorState())
        .thenReturn(new AccumulatorState(genesisTxns.getTxns().size() + 1, HashUtils.random256()));
    when(proof.getStateVersion()).thenReturn((long) genesisTxns.getTxns().size() + 1);
    when(proof.getView()).thenReturn(View.of(1));
    var commandsAndProof = VerifiedTxnsAndProof.create(List.of(txn), proof);
    stateComputer.commit(commandsAndProof, null);

    // Act
    MempoolAdd mempoolAdd = MempoolAdd.create(txn);
    processor.handleMessage(getFirstPeer(), mempoolAdd, null);

    // Assert
    assertThat(systemCounters.get(CounterType.MEMPOOL_CURRENT_SIZE)).isZero();
  }

  @Test
  public void mempool_removes_conflicts_on_commit() throws Exception {
    // Arrange
    getInjector().injectMembers(this);
    ECKeyPair keyPair = ECKeyPair.generateNew();
    var txn = createTxn(keyPair, 2);
    MempoolAdd mempoolAdd = MempoolAdd.create(txn);
    processor.handleMessage(getFirstPeer(), mempoolAdd, null);

    // Act
    var txn2 = createTxn(keyPair, 1);
    var proof = mock(LedgerProof.class);
    when(proof.getAccumulatorState())
        .thenReturn(new AccumulatorState(genesisTxns.getTxns().size() + 1, HashUtils.random256()));
    when(proof.getStateVersion()).thenReturn((long) genesisTxns.getTxns().size() + 1);
    when(proof.getView()).thenReturn(View.of(1));
    var commandsAndProof = VerifiedTxnsAndProof.create(List.of(txn2), proof);
    stateComputer.commit(commandsAndProof, null);

    // Assert
    assertThat(systemCounters.get(CounterType.MEMPOOL_CURRENT_SIZE)).isZero();
  }

  @Test
  public void mempool_removes_multiple_conflicts_on_commit() throws Exception {
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
    when(proof.getAccumulatorState())
        .thenReturn(new AccumulatorState(genesisTxns.getTxns().size() + 1, HashUtils.random256()));
    when(proof.getStateVersion()).thenReturn((long) genesisTxns.getTxns().size() + 1);
    when(proof.getView()).thenReturn(View.of(1));
    var commandsAndProof = VerifiedTxnsAndProof.create(List.of(txn3), proof);
    stateComputer.commit(commandsAndProof, null);

    // Assert
    assertThat(systemCounters.get(CounterType.MEMPOOL_CURRENT_SIZE)).isZero();
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
    assertThat(systemCounters.get(CounterType.MEMPOOL_CURRENT_SIZE)).isEqualTo(1);

    assertThat(network.allMessages())
        .hasOnlyOneElementSatisfying(
            m -> assertThat(m.message()).isInstanceOf(MempoolAddSuccess.class));
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
