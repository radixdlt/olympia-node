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

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.modules.PersistedNodeForTestingModule;
import com.radixdlt.application.system.FeeTable;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.ViewQuorumReached;
import com.radixdlt.consensus.bft.ViewVotingResult.FormedQC;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.EventProcessorOnDispatch;
import com.radixdlt.environment.ProcessOnDispatch;
import com.radixdlt.environment.deterministic.DeterministicProcessor;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.harness.deterministic.NodeEvents;
import com.radixdlt.harness.deterministic.NodeEvents.NodeEventProcessor;
import com.radixdlt.harness.deterministic.NodeEventsModule;
import com.radixdlt.harness.deterministic.SafetyCheckerModule;
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
import com.radixdlt.sync.messages.local.LocalSyncRequest;
import com.radixdlt.utils.KeyComparator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
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

@RunWith(Parameterized.class)
public class OneNodeAlwaysAliveSafetyTest {
  private static final Logger logger = LogManager.getLogger();

  @Parameters
  public static Collection<Object[]> numNodes() {
    return List.of(new Object[][] {{5}});
  }

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private DeterministicNetwork network;
  private List<Supplier<Injector>> nodeCreators;
  private List<Injector> nodes = new ArrayList<>();
  private final List<ECKeyPair> nodeKeys;

  @Inject private NodeEvents nodeEvents;

  private int lastNodeToCommit;

  public OneNodeAlwaysAliveSafetyTest(int numNodes) {
    this.nodeKeys =
        Stream.generate(ECKeyPair::generateNew)
            .limit(numNodes)
            .sorted(Comparator.comparing(ECKeyPair::getPublicKey, KeyComparator.instance()))
            .toList();
  }

  @Before
  public void setup() {
    var allNodes = nodeKeys.stream().map(k -> BFTNode.create(k.getPublicKey())).toList();

    this.network =
        new DeterministicNetwork(
            allNodes,
            MessageSelector.firstSelector(),
            (message, queue) ->
                message.message() instanceof GetVerticesRequest
                    || message.message() instanceof LocalSyncRequest);

    Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(new TypeLiteral<ImmutableSet<BFTNode>>() {})
                    .toInstance(ImmutableSet.copyOf(allNodes));
              }

              @ProvidesIntoSet
              public NodeEventProcessor<?> updateChecker() {
                return new NodeEventProcessor<>(
                    ViewQuorumReached.class,
                    (node, viewQuorumReached) -> {
                      if (viewQuorumReached.votingResult() instanceof FormedQC
                          && ((FormedQC) viewQuorumReached.votingResult())
                              .getQC()
                              .getCommitted()
                              .isPresent()) {
                        lastNodeToCommit = network.lookup(node);
                      }
                    });
              }
            },
            new SafetyCheckerModule(),
            new NodeEventsModule())
        .injectMembers(this);

    this.nodeCreators =
        nodeKeys.stream().<Supplier<Injector>>map(k -> () -> createRunner(k, allNodes)).toList();

    for (var nodeCreator : nodeCreators) {
      this.nodes.add(nodeCreator.get());
    }
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
            Amount.ofTokens(1000000),
            Amount.ofTokens(10000)),
        MempoolConfig.asModule(10, 10),
        new MainnetForksModule(),
        new RadixEngineForksLatestOnlyModule(
            new RERulesConfig(
                Set.of("xrd"),
                Pattern.compile("[a-z0-9]+"),
                FeeTable.noFees(),
                1024 * 1024,
                OptionalInt.of(50),
                88,
                2,
                Amount.ofTokens(10),
                1,
                Amount.ofTokens(10),
                9800,
                10,
                MSG.maxLength())),
        new ForksModule(),
        new PersistedNodeForTestingModule(),
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(ECKeyPair.class).annotatedWith(Self.class).toInstance(ecKeyPair);
            bind(new TypeLiteral<List<BFTNode>>() {}).toInstance(allNodes);
            bind(PeersView.class).toInstance(Stream::of);
            bind(Environment.class)
                .toInstance(network.createSender(BFTNode.create(ecKeyPair.getPublicKey())));
            bindConstant()
                .annotatedWith(DatabaseLocation.class)
                .to(folder.getRoot().getAbsolutePath() + "/" + ecKeyPair.getPublicKey().toHex());
          }

          @ProvidesIntoSet
          @ProcessOnDispatch
          private EventProcessor<BFTCommittedUpdate> committedUpdateEventProcessor(
              @Self BFTNode node) {
            return nodeEvents.processor(node, BFTCommittedUpdate.class);
          }

          @ProvidesIntoSet
          private EventProcessorOnDispatch<?> viewQuorumReachedEventProcessor(@Self BFTNode node) {
            return nodeEvents.processorOnDispatch(node, ViewQuorumReached.class);
          }
        });
  }

  private void restartNode(int index) {
    this.network.dropMessages(m -> m.channelId().receiverIndex() == index);
    var injector = nodeCreators.get(index).get();
    this.nodes.set(index, injector);
  }

  private void startNode(int index) {
    var injector = nodes.get(index);
    ThreadContext.put("self", " " + injector.getInstance(Key.get(String.class, Self.class)));
    try {
      injector.getInstance(DeterministicProcessor.class).start();
    } finally {
      ThreadContext.remove("self");
    }
  }

  private void processNext() {
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
  }

  private void processUntilNextCommittedUpdate() {
    lastNodeToCommit = -1;

    while (lastNodeToCommit == -1) {
      processNext();
    }
  }

  private void processForCount(int messageCount) {
    for (int i = 0; i < messageCount; i++) {
      processNext();
    }
  }

  @Test
  public void dropper_and_crasher_adversares_should_not_cause_safety_failures() {
    // Start
    for (int i = 0; i < nodes.size(); i++) {
      this.startNode(i);
    }

    // Drop first proposal so view 2 will be committed
    this.network.dropMessages(m -> m.message() instanceof Proposal);

    // process until view 2 committed
    this.processUntilNextCommittedUpdate();

    // Restart all except last committed
    logger.info("Restarting...");
    for (int i = 0; i < nodes.size(); i++) {
      if (i != this.lastNodeToCommit) {
        this.restartNode(i);
      }
    }
    for (int i = 0; i < nodes.size(); i++) {
      if (i != this.lastNodeToCommit) {
        this.startNode(i);
      }
    }

    // If nodes restart with correct safety precautions then view 1 should be skipped
    // otherwise, this will cause failure
    this.processForCount(5000);
  }
}
