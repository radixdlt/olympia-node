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
import com.google.inject.Singleton;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.sync.CommittedReader;
import com.radixdlt.sync.VerifiableCommandsAndProof;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Stream;

@Singleton
public final class StateComputerWithSometimesBadHashCommittedReader implements StateComputer, CommittedReader {
	private final TreeMap<Long, VerifiedCommandsAndProof> commandsAndProof = new TreeMap<>();
	private final Random random = new Random();

	@Override
	public boolean prepare(VerifiedVertex vertex) {
		return false;
	}

	@Override
	public Optional<BFTValidatorSet> commit(VerifiedCommandsAndProof verifiedCommandsAndProof) {
		commandsAndProof.put(verifiedCommandsAndProof.getFirstVersion(), verifiedCommandsAndProof);
		return Optional.empty();
	}

	@Override
	public VerifiableCommandsAndProof getNextCommittedCommands(VerifiedLedgerHeaderAndProof currentHeader, int batchSize) {
		Entry<Long, VerifiedCommandsAndProof> entry = commandsAndProof.higherEntry(currentHeader.getStateVersion());
		if (entry != null) {
			ImmutableList<Command> commands;
			VerifiedCommandsAndProof commandsToSendBack = entry.getValue().truncateFromVersion(currentHeader.getStateVersion());
			if (random.nextBoolean()) {
				 commands = Stream.generate(() -> new Command(new byte[]{0})).limit(commandsToSendBack.size())
					.collect(ImmutableList.toImmutableList());
			} else {
				commands = commandsToSendBack.getCommands();
			}
			return new VerifiableCommandsAndProof(commands, currentHeader, commandsToSendBack.getHeader());
		}

		return null;
	}

}
