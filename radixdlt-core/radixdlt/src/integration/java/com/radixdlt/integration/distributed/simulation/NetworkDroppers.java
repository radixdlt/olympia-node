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

package com.radixdlt.integration.distributed.simulation;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.integration.distributed.simulation.network.MessageDropper;
import com.radixdlt.integration.distributed.simulation.network.FProposalsPerViewDropper;
import com.radixdlt.integration.distributed.simulation.network.OneNodePerEpochLedgerStatusUpdateDropper;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork.MessageInTransit;
import java.util.Random;
import java.util.function.Predicate;

public final class NetworkDroppers {
	// TODO: This doesn't work with epochs yet
	public static Module fRandomProposalsPerViewDropped() {
		return new AbstractModule() {
			@ProvidesIntoSet
			Predicate<MessageInTransit> dropper(ImmutableList<BFTNode> nodes, Random random) {
				return new FProposalsPerViewDropper(nodes, random);
			}
		};
	}

	// TODO: This doesn't work with epochs yet
	public static Module fNodesAllReceivedProposalsDropped() {
		return new AbstractModule() {
			@ProvidesIntoSet
			Predicate<MessageInTransit> dropper(ImmutableList<BFTNode> nodes) {
				return new FProposalsPerViewDropper(nodes);
			}
		};
	}

	public static Module dropAllMessagesForOneNode(long durationMillis, long timeBetweenMillis) {
		return new AbstractModule() {
			@ProvidesIntoSet
			Predicate<MessageInTransit> dropper(ImmutableList<BFTNode> nodes) {
				return msg -> {
					if (msg.getSender().equals(msg.getReceiver())) {
						return false;
					}

					if (!msg.getSender().equals(nodes.get(0)) && !msg.getReceiver().equals(nodes.get(0))) {
						return false;
					}

					long current = System.currentTimeMillis() % (durationMillis + timeBetweenMillis);
					return current < durationMillis;
				};
			}
		};
	}

	public static Module randomVotesAndViewTimeoutsDropped(double drops) {
		return new AbstractModule() {
			@ProvidesIntoSet
			Predicate<MessageInTransit> dropper(Random random) {
				return new MessageDropper(random, drops, Vote.class);
			}
		};
	}

	public static Module oneNodePerEpochLedgerStatusUpdateDropped() {
		return new AbstractModule() {
			@ProvidesIntoSet
			Predicate<MessageInTransit> dropper() {
				return new OneNodePerEpochLedgerStatusUpdateDropper();
			}
		};
	}

	public static Module bftSyncMessagesDropped(double dropRate) {
		return new AbstractModule() {
			@ProvidesIntoSet
			Predicate<MessageInTransit> dropper(Random random) {
				return new MessageDropper(
					random,
					dropRate,
					GetVerticesResponse.class,
					GetVerticesErrorResponse.class,
					GetVerticesRequest.class
				);
			}
		};
	}

	public static Module bftSyncMessagesDropped() {
		return new AbstractModule() {
			@ProvidesIntoSet
			Predicate<MessageInTransit> dropper() {
				return new MessageDropper(
					GetVerticesResponse.class,
					GetVerticesErrorResponse.class,
					GetVerticesRequest.class
				);
			}
		};
	}

	public static Module dropAllProposals() {
		return new AbstractModule() {
			@ProvidesIntoSet
			Predicate<MessageInTransit> dropper() {
				return new MessageDropper(
					Proposal.class
				);
			}
		};
	}

	private NetworkDroppers() {
		throw new UnsupportedOperationException("Cannot instantiate.");
	}
}
