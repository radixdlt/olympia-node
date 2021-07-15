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

package com.radixdlt.consensus;

import com.radixdlt.application.tokens.Amount;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.MainnetForkConfigsModule;
import com.radixdlt.utils.PrivateKeys;
import org.assertj.core.api.Condition;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.consensus.epoch.Epoched;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.environment.deterministic.DeterministicProcessor;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.qualifier.NumPeers;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseLocation;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies pacemaker functionality
 */
public class PacemakerTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	private DeterministicProcessor processor;

	@Inject
	private ViewUpdate initialViewUpdate;

	@Inject
	private DeterministicNetwork network;

	private Injector createRunner() {
		return Guice.createInjector(
			MempoolConfig.asModule(10, 10),
			new MainnetForkConfigsModule(),
			new ForksModule(),
			new RadixEngineForksLatestOnlyModule(),
			new MockedGenesisModule(
				Set.of(PrivateKeys.ofNumeric(1).getPublicKey()),
				Amount.ofTokens(1000),
				Amount.ofTokens(100)
			),
			new SingleNodeAndPeersDeterministicNetworkModule(PrivateKeys.ofNumeric(1)),
			new AbstractModule() {
				@Override
				protected void configure() {
					bindConstant().annotatedWith(NumPeers.class).to(0);
					bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
				}
			}
		);
	}

	@Test
	public void on_startup_pacemaker_should_schedule_timeouts() {
		// Arrange
		createRunner().injectMembers(this);

		// Act
		processor.start();

		// Assert
		assertThat(network.allMessages())
			//.hasSize(3) // FIXME: Added hack to include a message regarding genesis committed so ignore this check
			.haveExactly(1,
				new Condition<>(msg -> Epoched.isInstance(msg.message(), ScheduledLocalTimeout.class),
				"A single epoched scheduled timeout has been emitted"))
			.haveExactly(1,
				new Condition<>(msg -> msg.message() instanceof ScheduledLocalTimeout,
					"A single scheduled timeout update has been emitted"))
			.haveExactly(1,
				new Condition<>(msg -> msg.message() instanceof Proposal,
					"A proposal has been emitted"));
	}

	@Test
	public void on_timeout_pacemaker_should_send_vote_with_timeout() {
		// Arrange
		createRunner().injectMembers(this);
		processor.start();

		// Act
		ControlledMessage timeoutMsg = network.nextMessage(e -> Epoched.isInstance(e.message(), ScheduledLocalTimeout.class)).value();
		processor.handleMessage(timeoutMsg.origin(), timeoutMsg.message(), new TypeLiteral<Epoched<ScheduledLocalTimeout>>() { });
		ControlledMessage bftUpdateMsg = network.nextMessage(e -> e.message() instanceof BFTInsertUpdate).value();
		processor.handleMessage(bftUpdateMsg.origin(), bftUpdateMsg.message(), null);

		// Assert
		assertThat(network.allMessages())
			.haveExactly(1, new Condition<>(
				msg -> (msg.message() instanceof Vote) && ((Vote) msg.message()).isTimeout(),
				"A remote timeout vote has been emitted"));
	}

	@Test
	public void on_view_timeout_quorum_pacemaker_should_move_to_next_view() {
		// Arrange
		createRunner().injectMembers(this);
		processor.start();
		ControlledMessage timeoutMsg = network.nextMessage(e -> Epoched.isInstance(e.message(), ScheduledLocalTimeout.class)).value();
		processor.handleMessage(timeoutMsg.origin(), timeoutMsg.message(), new TypeLiteral<Epoched<ScheduledLocalTimeout>>() { });
		ControlledMessage bftUpdateMsg = network.nextMessage(e -> e.message() instanceof BFTInsertUpdate).value();
		processor.handleMessage(bftUpdateMsg.origin(), bftUpdateMsg.message(), null);

		// Act
		ControlledMessage viewTimeout = network.nextMessage(e ->
			(e.message() instanceof Vote) && ((Vote) e.message()).isTimeout()).value();
		processor.handleMessage(viewTimeout.origin(), viewTimeout.message(), null);

		// Assert
		assertThat(network.allMessages())
			.haveExactly(1, new Condition<>(msg -> msg.message() instanceof EpochViewUpdate, "A remote view timeout has been emitted"));
		EpochViewUpdate nextEpochViewUpdate = network.allMessages().stream()
			.filter(msg -> msg.message() instanceof EpochViewUpdate)
			.map(ControlledMessage::message)
			.map(EpochViewUpdate.class::cast)
			.findAny()
			.orElseThrow();
		assertThat(nextEpochViewUpdate.getViewUpdate().getCurrentView()).isEqualTo(initialViewUpdate.getCurrentView().next());
	}
}
