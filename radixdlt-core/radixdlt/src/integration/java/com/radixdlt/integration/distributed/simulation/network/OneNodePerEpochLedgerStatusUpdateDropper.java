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

package com.radixdlt.integration.distributed.simulation.network;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork.MessageInTransit;
import com.radixdlt.sync.messages.remote.LedgerStatusUpdate;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Drops all epoch ledger status update messages from the first node to send an epoch response
 * for a given epoch
 */
public class OneNodePerEpochLedgerStatusUpdateDropper implements Predicate<MessageInTransit> {
	private final Map<Long, BFTNode> nodeToDrop = new HashMap<>();

	@Override
	public boolean test(MessageInTransit messageInTransit) {
		if (!(messageInTransit.getContent() instanceof LedgerStatusUpdate)) {
			return false;
		}

		final var ledgerStatusUpdate = (LedgerStatusUpdate) messageInTransit.getContent();

		final var node = nodeToDrop.putIfAbsent(ledgerStatusUpdate.getHeader().getEpoch(), messageInTransit.getSender());
		if (node == null) {
			return true;
		}

		return messageInTransit.getSender().equals(node);
	}
}
