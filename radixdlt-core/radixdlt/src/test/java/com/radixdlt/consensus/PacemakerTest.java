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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.consensus.epoch.Epoched;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.deterministic.ControlledSenderFactory;
import com.radixdlt.environment.deterministic.DeterministicEpochsConsensusProcessor;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.recovery.ModuleForRecoveryTests;
import com.radixdlt.statecomputer.EpochCeilingView;
import java.util.List;

import com.radixdlt.store.DatabaseLocation;
import org.assertj.core.api.Condition;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Verifies pacemaker functionality
 */
public class PacemakerTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private DeterministicNetwork network;
	private ECKeyPair ecKeyPair = ECKeyPair.generateNew();

	@Inject
	private DeterministicEpochsConsensusProcessor processor;

	@Inject
	private ViewUpdate initialViewUpdate;

	public PacemakerTest() {
		this.network = new DeterministicNetwork(
			List.of(BFTNode.create(ecKeyPair.getPublicKey())),
			MessageSelector.firstSelector(),
			MessageMutator.nothing()
		);
	}

	private Injector createRunner(ECKeyPair ecKeyPair) {
		final BFTNode self = BFTNode.create(ecKeyPair.getPublicKey());

		return Guice.createInjector(
			new AbstractModule() {
				@Override
				protected void configure() {
					bind(HashSigner.class).toInstance(ecKeyPair::sign);
					bind(BFTNode.class).annotatedWith(Self.class).toInstance(self);
					bind(new TypeLiteral<List<BFTNode>>() { }).toInstance(ImmutableList.of(self));
					bind(ControlledSenderFactory.class).toInstance(network::createSender);
					bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(View.of(10L));
					bindConstant().annotatedWith(DatabaseLocation.class)
						.to(folder.getRoot().getAbsolutePath() + "/RADIXDB_RECOVERY_TEST_" + self);
				}
			},
			ModuleForRecoveryTests.create()
		);
	}

	@Test
	public void on_startup_pacemaker_should_schedule_timeouts() {
		// Arrange
		createRunner(ecKeyPair).injectMembers(this);

		// Act
		processor.start();

		// Assert
		assertThat(network.allMessages())
			.hasSize(3)
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
		createRunner(ecKeyPair).injectMembers(this);
		processor.start();

		// Act
		ControlledMessage timeoutMsg = network.nextMessage(e -> Epoched.isInstance(e.message(), ScheduledLocalTimeout.class)).value();
		processor.handleMessage(timeoutMsg.origin(), timeoutMsg.message());
		ControlledMessage bftUpdateMsg = network.nextMessage(e -> e.message() instanceof BFTInsertUpdate).value();
		processor.handleMessage(bftUpdateMsg.origin(), bftUpdateMsg.message());

		// Assert
		assertThat(network.allMessages())
			.haveExactly(1, new Condition<>(
				msg -> (msg.message() instanceof Vote) && ((Vote) msg.message()).isTimeout(),
				"A remote timeout vote has been emitted"));
	}

	@Test
	public void on_view_timeout_quorum_pacemaker_should_move_to_next_view() {
		// Arrange
		createRunner(ecKeyPair).injectMembers(this);
		processor.start();
		ControlledMessage timeoutMsg = network.nextMessage(e -> Epoched.isInstance(e.message(), ScheduledLocalTimeout.class)).value();
		processor.handleMessage(timeoutMsg.origin(), timeoutMsg.message());
		ControlledMessage bftUpdateMsg = network.nextMessage(e -> e.message() instanceof BFTInsertUpdate).value();
		processor.handleMessage(bftUpdateMsg.origin(), bftUpdateMsg.message());

		// Act
		ControlledMessage viewTimeout = network.nextMessage(e ->
			(e.message() instanceof Vote) && ((Vote) e.message()).isTimeout()).value();
		processor.handleMessage(viewTimeout.origin(), viewTimeout.message());

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
