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

package com.radixdlt.integration.targeted.mempool;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.radixdlt.modules.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.deterministic.DeterministicProcessor;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.integration.Slow;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.MainnetForksModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.utils.PrivateKeys;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

/**
 * Test which fills a mempool and then empties it checking to make sure there are no stragglers left
 * behind.
 */
@Category(Slow.class)
public final class MempoolFillAndEmptyTest {
  private static final ECKeyPair TEST_KEY = PrivateKeys.ofNumeric(1);
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Inject private DeterministicProcessor processor;
  @Inject private DeterministicNetwork network;
  @Inject private EventDispatcher<MempoolFillerUpdate> mempoolFillerUpdateEventDispatcher;
  @Inject private EventDispatcher<ScheduledMempoolFill> scheduledMempoolFillEventDispatcher;
  @Inject private SystemCounters systemCounters;

  private Injector createInjector() {
    return Guice.createInjector(
        MempoolConfig.asModule(1000, 10),
        new MainnetForksModule(),
        new RadixEngineForksLatestOnlyModule(RERulesConfig.testingDefault()),
        new ForksModule(),
        new SingleNodeAndPeersDeterministicNetworkModule(TEST_KEY, 0),
        new MockedGenesisModule(
            Set.of(TEST_KEY.getPublicKey()), Amount.ofTokens(10000000000L), Amount.ofTokens(1000)),
        new MempoolFillerModule(),
        new AbstractModule() {
          @Override
          protected void configure() {
            bindConstant()
                .annotatedWith(DatabaseLocation.class)
                .to(folder.getRoot().getAbsolutePath());
          }
        });
  }

  private void fillAndEmptyMempool() {
    while (systemCounters.get(SystemCounters.CounterType.MEMPOOL_CURRENT_SIZE) < 1000) {
      ControlledMessage msg = network.nextMessage().value();
      processor.handleMessage(msg.origin(), msg.message(), msg.typeLiteral());
      if (msg.message() instanceof EpochViewUpdate) {
        scheduledMempoolFillEventDispatcher.dispatch(ScheduledMempoolFill.create());
      }
    }

    for (int i = 0; i < 10000; i++) {
      ControlledMessage msg = network.nextMessage().value();
      processor.handleMessage(msg.origin(), msg.message(), msg.typeLiteral());
      if (systemCounters.get(SystemCounters.CounterType.MEMPOOL_CURRENT_SIZE) == 0) {
        break;
      }
    }

    assertThat(systemCounters.get(SystemCounters.CounterType.MEMPOOL_CURRENT_SIZE)).isZero();
  }

  @Test
  public void check_that_full_mempool_empties_itself() {
    createInjector().injectMembers(this);
    processor.start();

    mempoolFillerUpdateEventDispatcher.dispatch(MempoolFillerUpdate.enable(50, true));

    for (int i = 0; i < 10; i++) {
      fillAndEmptyMempool();
    }

    assertThat(
            systemCounters.get(SystemCounters.CounterType.RADIX_ENGINE_INVALID_PROPOSED_COMMANDS))
        .isZero();
  }
}
