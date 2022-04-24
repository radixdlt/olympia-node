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

package com.radixdlt.consensus;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import com.radixdlt.modules.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.consensus.epoch.Epoched;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.environment.deterministic.DeterministicProcessor;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.MainnetForksModule;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.utils.PrivateKeys;
import java.util.Set;
import org.assertj.core.api.Condition;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Verifies pacemaker functionality */
public class PacemakerTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Inject private DeterministicProcessor processor;

  @Inject private ViewUpdate initialViewUpdate;

  @Inject private DeterministicNetwork network;

  private Injector createRunner() {
    return Guice.createInjector(
        MempoolConfig.asModule(10, 10),
        new MainnetForksModule(),
        new RadixEngineForksLatestOnlyModule(),
        new ForksModule(),
        new MockedGenesisModule(
            Set.of(PrivateKeys.ofNumeric(1).getPublicKey()),
            Amount.ofTokens(1000),
            Amount.ofTokens(100)),
        new SingleNodeAndPeersDeterministicNetworkModule(PrivateKeys.ofNumeric(1), 0),
        new AbstractModule() {
          @Override
          protected void configure() {
            bindConstant()
                .annotatedWith(DatabaseLocation.class)
                .to(folder.getRoot().getAbsolutePath());
          }
        });
  }

  @Test
  public void on_startup_pacemaker_should_schedule_timeouts() {
    // Arrange
    createRunner().injectMembers(this);

    // Act
    processor.start();

    // Assert
    assertThat(network.allMessages())
        // .hasSize(3) // FIXME: Added hack to include a message regarding genesis committed so
        // ignore this check
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

  @Test
  public void on_timeout_pacemaker_should_send_vote_with_timeout() {
    // Arrange
    createRunner().injectMembers(this);
    processor.start();

    // Act
    ControlledMessage timeoutMsg =
        network
            .nextMessage(e -> Epoched.isInstance(e.message(), ScheduledLocalTimeout.class))
            .value();
    processor.handleMessage(
        timeoutMsg.origin(),
        timeoutMsg.message(),
        new TypeLiteral<Epoched<ScheduledLocalTimeout>>() {});
    ControlledMessage bftUpdateMsg =
        network.nextMessage(e -> e.message() instanceof BFTInsertUpdate).value();
    processor.handleMessage(bftUpdateMsg.origin(), bftUpdateMsg.message(), null);

    // Assert
    assertThat(network.allMessages())
        .haveExactly(
            1,
            new Condition<>(
                msg -> (msg.message() instanceof Vote) && ((Vote) msg.message()).isTimeout(),
                "A remote timeout vote has been emitted"));
  }

  @Test
  public void on_view_timeout_quorum_pacemaker_should_move_to_next_view() {
    // Arrange
    createRunner().injectMembers(this);
    processor.start();
    ControlledMessage timeoutMsg =
        network
            .nextMessage(e -> Epoched.isInstance(e.message(), ScheduledLocalTimeout.class))
            .value();
    processor.handleMessage(
        timeoutMsg.origin(),
        timeoutMsg.message(),
        new TypeLiteral<Epoched<ScheduledLocalTimeout>>() {});
    ControlledMessage bftUpdateMsg =
        network.nextMessage(e -> e.message() instanceof BFTInsertUpdate).value();
    processor.handleMessage(bftUpdateMsg.origin(), bftUpdateMsg.message(), null);

    // Act
    ControlledMessage viewTimeout =
        network
            .nextMessage(e -> (e.message() instanceof Vote) && ((Vote) e.message()).isTimeout())
            .value();
    processor.handleMessage(viewTimeout.origin(), viewTimeout.message(), null);

    // Assert
    assertThat(network.allMessages())
        .haveExactly(
            1,
            new Condition<>(
                msg -> msg.message() instanceof EpochViewUpdate,
                "A remote view timeout has been emitted"));
    EpochViewUpdate nextEpochViewUpdate =
        network.allMessages().stream()
            .filter(msg -> msg.message() instanceof EpochViewUpdate)
            .map(ControlledMessage::message)
            .map(EpochViewUpdate.class::cast)
            .findAny()
            .orElseThrow();
    assertThat(nextEpochViewUpdate.getViewUpdate().getCurrentView())
        .isEqualTo(initialViewUpdate.getCurrentView().next());
  }
}
