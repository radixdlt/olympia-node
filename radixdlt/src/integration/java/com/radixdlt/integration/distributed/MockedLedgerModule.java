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

package com.radixdlt.integration.distributed;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.bft.PreparedVertex;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.ledger.StateComputerLedger.PreparedCommand;
import com.radixdlt.ledger.VerifiedCommandsAndProof;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.consensus.LedgerHeader;
import java.util.LinkedList;
import java.util.Optional;

public class MockedLedgerModule extends AbstractModule {
	@Override
	public void configure() {
		bind(NextCommandGenerator.class).toInstance((view, aids) -> null);
	}

	@Provides
	@Singleton
	Ledger syncedLedger(Hasher hasher) {
		return new Ledger() {
			@Override
			public Optional<PreparedVertex> prepare(LinkedList<PreparedVertex> previous, VerifiedVertex vertex) {
				final long timestamp = vertex.getQC().getTimestampedSignatures().weightedTimestamp();
				final LedgerHeader ledgerHeader = vertex.getParentHeader().getLedgerHeader()
					.updateViewAndTimestamp(vertex.getView(), timestamp);

				return Optional.of(vertex
					.withHeader(ledgerHeader)
					.andCommands(
						vertex.getCommand()
							.<PreparedCommand>map(cmd -> new MockPrepared(cmd, hasher.hash(cmd)))
							.map(ImmutableList::of)
							.orElse(ImmutableList.of()),
						ImmutableMap.of()
				));
			}

			@Override
			public void commit(ImmutableList<PreparedVertex> vertices, HighQC highQC, ImmutableSet<HashCode> prunedVertices) {
				// Nothing to do here
			}

			@Override
			public void commit(VerifiedCommandsAndProof command) {
				// Nothing to do here
			}
		};
	}
}
