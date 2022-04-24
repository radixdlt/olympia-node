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

package com.radixdlt.integration.targeted.recovery;

import static com.radixdlt.constraintmachine.REInstruction.REMicroOp.MSG;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.radixdlt.modules.PersistedNodeForTestingModule;
import com.radixdlt.application.system.FeeTable;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.deterministic.DeterministicProcessor;
import com.radixdlt.environment.deterministic.LastEventsModule;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageQueue;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.MainnetForksModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.sync.messages.local.SyncCheckTrigger;
import com.radixdlt.utils.KeyComparator;
import io.reactivex.rxjava3.schedulers.Timed;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Various liveness+recovery tests */
@RunWith(Parameterized.class)
public class RecoveryLivenessTest {
  private static final Logger logger = LogManager.getLogger();

  @Parameters
  public static Collection<Object[]> numNodes() {
    return List.of(new Object[][] {{1, 88L}});
  }

  // The following classes are created as a workaround as gradle cannot run the tests inside a test
  // class in parallel. We can achieve some level of parallelism splitting the tests across
  // different test classes.

  @RunWith(Parameterized.class)
  public static class RecoveryLivenessTest2 extends RecoveryLivenessTest {

    @Parameters
    public static Collection<Object[]> numNodes() {
      return List.of(new Object[][] {{2, 88L}});
    }

    public RecoveryLivenessTest2(int numNodes, long epochCeilingView) {
      super(numNodes, epochCeilingView);
    }
  }

  public static class RecoveryLivenessTest3 extends RecoveryLivenessTest {

    @Parameters
    public static Collection<Object[]> numNodes() {
      return List.of(new Object[][] {{2, 88L}});
    }

    public RecoveryLivenessTest3(int numNodes, long epochCeilingView) {
      super(numNodes, epochCeilingView);
    }
  }

  public static class RecoveryLivenessTest4 extends RecoveryLivenessTest {

    @Parameters
    public static Collection<Object[]> numNodes() {
      return List.of(new Object[][] {{4, 88L}});
    }

    public RecoveryLivenessTest4(int numNodes, long epochCeilingView) {
      super(numNodes, epochCeilingView);
    }
  }

  public static class RecoveryLivenessTest5 extends RecoveryLivenessTest {

    @Parameters
    public static Collection<Object[]> numNodes() {
      return List.of(new Object[][] {{2, 1L}});
    }

    public RecoveryLivenessTest5(int numNodes, long epochCeilingView) {
      super(numNodes, epochCeilingView);
    }
  }

  public static class RecoveryLivenessTest6 extends RecoveryLivenessTest {

    @Parameters
    public static Collection<Object[]> numNodes() {
      return List.of(new Object[][] {{10, 100L}});
    }

    public RecoveryLivenessTest6(int numNodes, long epochCeilingView) {
      super(numNodes, epochCeilingView);
    }
  }

  @Rule public TemporaryFolder folder = new TemporaryFolder();
  private DeterministicNetwork network;
  private List<Supplier<Injector>> nodeCreators;
  private List<Injector> nodes = new ArrayList<>();
  private final ImmutableList<ECKeyPair> nodeKeys;
  private final long epochCeilingView;
  private MessageMutator messageMutator;

  public RecoveryLivenessTest(int numNodes, long epochCeilingView) {
    this.nodeKeys =
        Stream.generate(ECKeyPair::generateNew)
            .limit(numNodes)
            .sorted(Comparator.comparing(ECKeyPair::getPublicKey, KeyComparator.instance()))
            .collect(ImmutableList.toImmutableList());
    this.epochCeilingView = epochCeilingView;
  }

  @Before
  public void setup() {
    this.messageMutator = MessageMutator.nothing();
    this.network =
        new DeterministicNetwork(
            nodeKeys.stream().map(k -> BFTNode.create(k.getPublicKey())).toList(),
            MessageSelector.firstSelector(),
            this::mutate);

    var allNodes = nodeKeys.stream().map(k -> BFTNode.create(k.getPublicKey())).toList();

    this.nodeCreators =
        nodeKeys.stream().<Supplier<Injector>>map(k -> () -> createRunner(k, allNodes)).toList();

    for (Supplier<Injector> nodeCreator : nodeCreators) {
      this.nodes.add(nodeCreator.get());
    }
    this.nodes.forEach(i -> i.getInstance(DeterministicProcessor.class).start());
  }

  boolean mutate(ControlledMessage message, MessageQueue queue) {
    return messageMutator.mutate(message, queue);
  }

  private void stopDatabase(Injector injector) {
    injector.getInstance(BerkeleyLedgerEntryStore.class).close();
    injector.getInstance(PersistentSafetyStateStore.class).close();
    injector.getInstance(DatabaseEnvironment.class).stop();
  }

  @After
  public void teardown() {
    this.nodes.forEach(this::stopDatabase);
  }

  private Injector createRunner(ECKeyPair ecKeyPair, List<BFTNode> allNodes) {
    return Guice.createInjector(
        new MockedGenesisModule(
            nodeKeys.stream().map(ECKeyPair::getPublicKey).collect(Collectors.toSet()),
            Amount.ofTokens(100000),
            Amount.ofTokens(1000)),
        MempoolConfig.asModule(10, 10),
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
        new PersistedNodeForTestingModule(),
        new LastEventsModule(EpochViewUpdate.class),
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(ECKeyPair.class).annotatedWith(Self.class).toInstance(ecKeyPair);
            bind(new TypeLiteral<List<BFTNode>>() {}).toInstance(allNodes);
            bind(Environment.class)
                .toInstance(network.createSender(BFTNode.create(ecKeyPair.getPublicKey())));
            bindConstant()
                .annotatedWith(DatabaseLocation.class)
                .to(folder.getRoot().getAbsolutePath() + "/" + ecKeyPair.getPublicKey().toHex());
          }

          @Provides
          private PeersView peersView(@Self BFTNode self) {
            return () ->
                allNodes.stream().filter(n -> !self.equals(n)).map(PeersView.PeerInfo::fromBftNode);
          }
        });
  }

  private void restartNode(int index) {
    this.network.dropMessages(m -> m.channelId().receiverIndex() == index);
    var injector = nodeCreators.get(index).get();
    stopDatabase(this.nodes.set(index, injector));
    withThreadCtx(injector, () -> injector.getInstance(DeterministicProcessor.class).start());
  }

  private void initSync() {
    for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
      final var injector = nodeCreators.get(nodeIndex).get();
      withThreadCtx(
          injector,
          () -> {
            // need to manually init sync check, normally sync runner schedules it periodically
            injector
                .getInstance(new Key<EventDispatcher<SyncCheckTrigger>>() {})
                .dispatch(SyncCheckTrigger.create());
          });
    }
  }

  private void withThreadCtx(Injector injector, Runnable r) {
    ThreadContext.put("self", " " + injector.getInstance(Key.get(String.class, Self.class)));
    try {
      r.run();
    } finally {
      ThreadContext.remove("self");
    }
  }

  private Timed<ControlledMessage> processNext() {
    var msg = this.network.nextMessage();
    logger.debug("Processing message {}", msg);

    int nodeIndex = msg.value().channelId().receiverIndex();
    var injector = this.nodes.get(nodeIndex);
    ThreadContext.put("self", " " + injector.getInstance(Key.get(String.class, Self.class)));
    try {
      injector
          .getInstance(DeterministicProcessor.class)
          .handleMessage(msg.value().origin(), msg.value().message(), msg.value().typeLiteral());
    } finally {
      ThreadContext.remove("self");
    }

    return msg;
  }

  private Optional<EpochView> lastCommitViewEmitted() {
    return network.allMessages().stream()
        .filter(msg -> msg.message() instanceof EpochViewUpdate)
        .map(msg -> (EpochViewUpdate) msg.message())
        .map(
            e ->
                new EpochView(
                    e.getEpoch(), e.getViewUpdate().getHighQC().highestCommittedQC().getView()))
        .max(Comparator.naturalOrder());
  }

  private EpochView latestEpochView() {
    return this.nodes.stream()
        .map(
            i ->
                i.getInstance(Key.get(new TypeLiteral<ClassToInstanceMap<Object>>() {}))
                    .getInstance(EpochViewUpdate.class))
        .map(e -> e == null ? new EpochView(0, View.genesis()) : e.getEpochView())
        .max(Comparator.naturalOrder())
        .orElse(new EpochView(0, View.genesis()));
  }

  private int processUntilNextCommittedEmitted(int maxSteps) {
    var lastCommitted = this.lastCommitViewEmitted().orElse(new EpochView(0, View.genesis()));
    int count = 0;
    int senderIndex;
    do {
      if (count > maxSteps) {
        throw new IllegalStateException("Already lost liveness");
      }

      var msg = processNext();
      senderIndex = msg.value().channelId().senderIndex();
      count++;
    } while (this.lastCommitViewEmitted().stream().noneMatch(v -> v.compareTo(lastCommitted) > 0));

    return senderIndex;
  }

  private void processForCount(int messageCount) {
    for (int i = 0; i < messageCount; i++) {
      processNext();
    }
  }

  /**
   * Given that one validator is always alive means that that validator will always have the latest
   * committed vertex which the validator can sync others with.
   */
  @Test
  public void liveness_check_when_restart_all_but_one_node() {
    var epochView = this.latestEpochView();

    for (int restart = 0; restart < 5; restart++) {
      processForCount(5000);

      var nextEpochView = latestEpochView();
      assertThat(nextEpochView).isGreaterThan(epochView);
      epochView = nextEpochView;

      logger.info("Restarting " + restart);
      for (int nodeIndex = 1; nodeIndex < nodes.size(); nodeIndex++) {
        restartNode(nodeIndex);
      }
      initSync();
    }

    assertThat(epochView.getEpoch()).isGreaterThan(1);
  }

  @Test
  public void liveness_check_when_restart_node_on_view_update_with_commit() {
    var epochView = this.latestEpochView();

    for (int restart = 0; restart < 5; restart++) {
      processForCount(5000);

      var nextEpochView = latestEpochView();
      assertThat(nextEpochView).isGreaterThan(epochView);
      epochView = nextEpochView;

      int nodeToRestart = processUntilNextCommittedEmitted(5000);

      logger.info("Restarting " + restart);
      for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
        if (nodeIndex != (nodeToRestart + 1) % nodes.size()) {
          restartNode(nodeIndex);
        }
      }
      initSync();
    }

    assertThat(epochView.getEpoch()).isGreaterThan(1);
  }

  @Test
  public void liveness_check_when_restart_all_nodes() {
    var epochView = this.latestEpochView();

    for (int restart = 0; restart < 5; restart++) {
      processForCount(5000);

      var nextEpochView = latestEpochView();
      assertThat(nextEpochView).isGreaterThan(epochView);
      epochView = nextEpochView;

      logger.info("Restarting " + restart);
      for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
        restartNode(nodeIndex);
      }
      initSync();
    }

    assertThat(epochView.getEpoch()).isGreaterThan(1);
  }

  /**
   * This test tests for recovery when there is a vertex chain > 3 due to timeouts. Probably won't
   * be an issue once timeout certificates implemented.
   */
  @Test
  public void liveness_check_when_restart_all_nodes_and_f_nodes_down() {
    int f = (nodes.size() - 1) / 3;
    if (f <= 0) {
      // if f <= 0, this is equivalent to liveness_check_when_restart_all_nodes();
      return;
    }

    this.messageMutator =
        (message, queue) ->
            message.channelId().receiverIndex() < f || message.channelId().senderIndex() < f;

    var epochView = this.latestEpochView();

    for (int restart = 0; restart < 5; restart++) {
      processForCount(5000);

      var nextEpochView = latestEpochView();
      assertThat(nextEpochView).isGreaterThan(epochView);
      epochView = nextEpochView;

      logger.info("Restarting " + restart);
      for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
        restartNode(nodeIndex);
      }
      initSync();
    }

    assertThat(epochView.getEpoch()).isGreaterThan(1);
  }
}
