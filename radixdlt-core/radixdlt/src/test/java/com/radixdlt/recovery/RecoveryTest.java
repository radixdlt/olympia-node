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

package com.radixdlt.recovery;

import static com.radixdlt.constraintmachine.REInstruction.REMicroOp.MSG;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.radixdlt.modules.PersistedNodeForTestingModule;
import com.radixdlt.application.system.FeeTable;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.consensus.epoch.Epoched;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.deterministic.DeterministicProcessor;
import com.radixdlt.environment.deterministic.LastEventsModule;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.MainnetForksModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.LastEpochProof;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.sync.CommittedReader;
import io.reactivex.rxjava3.schedulers.Timed;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.assertj.core.api.Condition;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Verifies that on restarts (simulated via creation of new injectors) that the application state is
 * the same as last seen.
 */
@RunWith(Parameterized.class)
public class RecoveryTest {

  @Parameters
  public static Collection<Object[]> parameters() {
    return List.of(
        new Object[] {10L, 80},
        new Object[] {10L, 90},
        new Object[] {10L, 100},
        new Object[] {10L, 500},
        new Object[] {1000000L, 100});
  }

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private DeterministicNetwork network;
  private Injector currentInjector;
  private ECKeyPair ecKeyPair = ECKeyPair.generateNew();
  private final long epochCeilingView;
  private final int processForCount;

  public RecoveryTest(long epochCeilingView, int processForCount) {
    this.epochCeilingView = epochCeilingView;
    this.processForCount = processForCount;
    this.network =
        new DeterministicNetwork(
            List.of(BFTNode.create(ecKeyPair.getPublicKey())),
            MessageSelector.firstSelector(),
            MessageMutator.nothing());
  }

  @Before
  public void setup() {
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
        new MockedGenesisModule(
            Set.of(ecKeyPair.getPublicKey()), Amount.ofTokens(1000), Amount.ofTokens(100)),
        new MainnetForksModule(),
        new RadixEngineForksLatestOnlyModule(
            new RERulesConfig(
                Set.of("xrd"),
                Pattern.compile("[a-z0-9]+"),
                FeeTable.noFees(),
                1024 * 1024,
                OptionalInt.of(50),
                epochCeilingView,
                2,
                Amount.ofTokens(10),
                1,
                Amount.ofTokens(10),
                9800,
                10,
                MSG.maxLength())),
        new ForksModule(),
        MempoolConfig.asModule(10, 10),
        new LastEventsModule(EpochViewUpdate.class, Vote.class),
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(PeersView.class).toInstance(Stream::of);
            bind(ECKeyPair.class).annotatedWith(Self.class).toInstance(ecKeyPair);
            bind(new TypeLiteral<List<BFTNode>>() {}).toInstance(ImmutableList.of(self));
            bind(Environment.class).toInstance(network.createSender(BFTNode.create(self.getKey())));
            bindConstant()
                .annotatedWith(DatabaseLocation.class)
                .to(folder.getRoot().getAbsolutePath() + "/RADIXDB_RECOVERY_TEST_" + self);
          }
        },
        new PersistedNodeForTestingModule());
  }

  private RadixEngine<LedgerAndBFTProof> getRadixEngine() {
    return currentInjector.getInstance(
        Key.get(new TypeLiteral<RadixEngine<LedgerAndBFTProof>>() {}));
  }

  private CommittedReader getCommittedReader() {
    return currentInjector.getInstance(CommittedReader.class);
  }

  private EpochView getLastEpochView() {
    return currentInjector
        .getInstance(Key.get(new TypeLiteral<ClassToInstanceMap<Object>>() {}))
        .getInstance(EpochViewUpdate.class)
        .getEpochView();
  }

  private Vote getLastVote() {
    return currentInjector
        .getInstance(Key.get(new TypeLiteral<ClassToInstanceMap<Object>>() {}))
        .getInstance(Vote.class);
  }

  private void restartNode() {
    this.network.dropMessages(
        m -> m.channelId().receiverIndex() == 0 && m.channelId().senderIndex() == 0);
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
    LedgerProof restartedEpochProof =
        currentInjector.getInstance(Key.get(LedgerProof.class, LastEpochProof.class));

    assertThat(restartedEpochProof.isEndOfEpoch()).isTrue();
    assertThat(
            restartedEpochProof.getEpoch() == epochView.getEpoch() - 1
                || (restartedEpochProof.getEpoch() == epochView.getEpoch()
                    && epochView.getView().number() > epochCeilingView + 3))
        .isTrue();
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
                    && vote.getView().equals(View.of(epochCeilingView + 3))))
        .isTrue();
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
                "A single epoched scheduled timeout has been emitted"))
        .haveExactly(
            1,
            new Condition<>(
                msg -> msg.message() instanceof ScheduledLocalTimeout,
                "A single scheduled timeout update has been emitted"))
        .haveExactly(
            1,
            new Condition<>(
                msg -> msg.message() instanceof Proposal, "A proposal has been emitted"));
  }
}
