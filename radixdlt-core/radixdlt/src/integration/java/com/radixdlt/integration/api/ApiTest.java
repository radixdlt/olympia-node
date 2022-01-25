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

package com.radixdlt.integration.api;

import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import com.radixdlt.PersistedNodeForTestingModule;
import com.radixdlt.api.core.reconstruction.BerkeleyRecoverableProcessedTxnStore;
import com.radixdlt.application.system.FeeTable;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.deterministic.LastEventsModule;
import com.radixdlt.environment.deterministic.MultiNodeDeterministicRunner;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.integration.FailOnEvent;
import com.radixdlt.integration.Slow;
import com.radixdlt.integration.api.actors.ApiBalanceToRadixEngineChecker;
import com.radixdlt.integration.api.actors.ApiTxnSubmitter;
import com.radixdlt.integration.api.actors.BalanceReconciler;
import com.radixdlt.integration.api.actors.NativeTokenRewardsChecker;
import com.radixdlt.integration.api.actors.RandomNodeRestarter;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.mempool.MempoolRelayTrigger;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.networks.Network;
import com.radixdlt.networks.NetworkId;
import com.radixdlt.statecomputer.InvalidProposedTxn;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.ForkOverwritesWithShorterEpochsModule;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.MainnetForkConfigsModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.berkeley.BerkeleyAdditionalStore;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.sync.messages.local.SyncCheckTrigger;
import com.radixdlt.utils.PrivateKeys;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Test which runs a 20 node consensus network. Random transactions are submitted and nodes are
 * rebooted while checks occur on the api to make sure that invariants are not broken.
 */
public abstract class ApiTest {
  private static final Logger logger = LogManager.getLogger();
  private static final int ACTION_ROUNDS = 5000;
  private static final RERulesConfig config =
      RERulesConfig.testingDefault().overrideMaxSigsPerRound(2);
  private static final Amount PER_BYTE_FEE = Amount.ofMicroTokens(2);
  private static final List<ActorConfiguration> ACTOR_CONFIGURATIONS =
      List.of(
          new ActorConfiguration(ApiTxnSubmitter::new, 1, 2),
          new ActorConfiguration(BalanceReconciler::new, 1, 10),
          new ActorConfiguration(RandomNodeRestarter::new, 1, 10),
          new ActorConfiguration(NativeTokenRewardsChecker::new, 1, 100),
          new ActorConfiguration(ApiBalanceToRadixEngineChecker::new, 1, 200));

  // The following class is created as a workaround as gradle cannot run the tests inside a test
  // class in parallel. We can achieve some level of parallelism splitting the tests across
  // different test classes.

  @Category(Slow.class)
  public static class ApiTest0 extends ApiTest {
    public ApiTest0() {
      super(new RadixEngineForksLatestOnlyModule(config.overrideMaxRounds(100)), null);
    }
  }


  @Category(Slow.class)
  public static class ApiTest1 extends ApiTest {
    public ApiTest1() {
      super(new RadixEngineForksLatestOnlyModule(config), null);
    }
  }

  @Category(Slow.class)
  public static class ApiTest2 extends ApiTest {
    public ApiTest2() {
      super(
              new ForkOverwritesWithShorterEpochsModule(config),
              new ForkOverwritesWithShorterEpochsModule(config.removeSigsPerRoundLimit())
      );
    }
  }


  @Category(Slow.class)
  public static class ApiTest3 extends ApiTest {
    public ApiTest3() {
      super(
              new RadixEngineForksLatestOnlyModule(
                      config
                              .overrideMaxRounds(100)
                              .overrideFeeTable(FeeTable.create(PER_BYTE_FEE, Map.of()))),
              null);
    }
  }

  @Category(Slow.class)
  public static class ApiTest4 extends ApiTest {
    public ApiTest4() {
      super(
              new ForkOverwritesWithShorterEpochsModule(
                      config.overrideFeeTable(FeeTable.create(PER_BYTE_FEE, Map.of()))),
              null);
    }
  }


  @Rule public TemporaryFolder folder = new TemporaryFolder();
  private DeterministicNetwork network;
  private final ImmutableList<ECKeyPair> nodeKeys;
  private final Module radixEngineConfiguration;
  private final Module byzantineModule;
  private MultiNodeDeterministicRunner deterministicRunner;

  public ApiTest(Module forkModule, Module byzantineModule) {
    this.nodeKeys = PrivateKeys.numeric(1).limit(20).collect(ImmutableList.toImmutableList());
    this.radixEngineConfiguration =
        Modules.combine(new MainnetForkConfigsModule(), new ForksModule(), forkModule);
    this.byzantineModule = byzantineModule;
  }

  @Before
  public void setup() {
    this.network =
        new DeterministicNetwork(
            nodeKeys.stream()
                .map(k -> BFTNode.create(k.getPublicKey()))
                .collect(Collectors.toList()),
            MessageSelector.firstSelector(),
            MessageMutator.nothing());

    List<BFTNode> allNodes =
        nodeKeys.stream().map(k -> BFTNode.create(k.getPublicKey())).collect(Collectors.toList());
    var nodeCreators =
        Streams.mapWithIndex(
                nodeKeys.stream(),
                (k, i) -> (Supplier<Injector>) () -> createRunner(i == 1, k, allNodes))
            .collect(Collectors.toList());

    deterministicRunner =
        new MultiNodeDeterministicRunner(nodeCreators, this::stopDatabase, network);
    deterministicRunner.start();
  }

  private void stopDatabase(Injector injector) {
    injector.getInstance(BerkeleyLedgerEntryStore.class).close();
    injector.getInstance(PersistentSafetyStateStore.class).close();
    injector.getInstance(DatabaseEnvironment.class).stop();
  }

  @After
  public void teardown() {
    deterministicRunner.tearDown();
  }

  private Injector createRunner(boolean byzantine, ECKeyPair ecKeyPair, List<BFTNode> allNodes) {
    var reConfig =
        byzantine && byzantineModule != null
            ? Modules.override(this.radixEngineConfiguration).with(byzantineModule)
            : this.radixEngineConfiguration;

    return Guice.createInjector(
        new MockedGenesisModule(
            nodeKeys.stream().map(ECKeyPair::getPublicKey).collect(Collectors.toSet()),
            Amount.ofTokens(100000),
            Amount.ofTokens(1000)),
        MempoolConfig.asModule(10, 10),
        reConfig,
        new PersistedNodeForTestingModule(),
        new LastEventsModule(LedgerUpdate.class),
        FailOnEvent.asModule(InvalidProposedTxn.class),
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(ECKeyPair.class).annotatedWith(Self.class).toInstance(ecKeyPair);
            bind(Environment.class)
                .toInstance(network.createSender(BFTNode.create(ecKeyPair.getPublicKey())));
            bindConstant()
                .annotatedWith(DatabaseLocation.class)
                .to(folder.getRoot().getAbsolutePath() + "/" + ecKeyPair.getPublicKey().toHex());
            bindConstant().annotatedWith(NetworkId.class).to(Network.LOCALNET.getId());
            bind(BerkeleyRecoverableProcessedTxnStore.class).in(Scopes.SINGLETON);
            Multibinder.newSetBinder(binder(), BerkeleyAdditionalStore.class)
                .addBinding()
                .to(BerkeleyRecoverableProcessedTxnStore.class);
          }

          @Provides
          private PeersView peersView(@Self BFTNode self) {
            return () ->
                allNodes.stream().filter(n -> !self.equals(n)).map(PeersView.PeerInfo::fromBftNode);
          }
        });
  }

  private static class RunningActor {
    private final DeterministicActor actor;
    private final int numerator;
    private final int denominator;
    private final Queue<String> lastResults = EvictingQueue.create(5);
    private int numActions;

    RunningActor(DeterministicActor actor, int numerator, int denominator) {
      this.actor = actor;
      this.numerator = numerator;
      this.denominator = denominator;
    }

    void tryExecute(MultiNodeDeterministicRunner runner, Random random) throws Exception {
      if (random.nextInt(denominator) < numerator) {
        var result = actor.execute(runner, random);
        lastResults.offer(result);
        logger.info("Actor {} -> {}", actor.getClass().getSimpleName(), result);
        numActions++;
      }
    }

    void printLastResults() {
      lastResults.forEach(result -> logger.info("\t{}", result));
    }
  }

  @Test
  public void run() throws Exception {
    var random = new Random(12345);

    var actors =
        ACTOR_CONFIGURATIONS.stream()
            .map(c -> new RunningActor(c.createActor(), c.getNumerator(), c.getDenominator()))
            .toList();

    for (int i = 0; i < ACTION_ROUNDS; i++) {
      deterministicRunner.processForCount(100);
      for (var actor : actors) {
        actor.tryExecute(deterministicRunner, random);
      }
      deterministicRunner.dispatchToAll(
          new Key<EventDispatcher<MempoolRelayTrigger>>() {}, MempoolRelayTrigger.create());
      deterministicRunner.dispatchToAll(
          new Key<EventDispatcher<SyncCheckTrigger>>() {}, SyncCheckTrigger.create());
    }

    logger.info("===Test Results===");
    for (var actor : actors) {
      logger.info("Actor {} count={}", actor.actor.getClass().getSimpleName(), actor.numActions);
      actor.printLastResults();
    }
  }
}
