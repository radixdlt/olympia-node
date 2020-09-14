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
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.ledger.StateComputerLedger.CommittedSender;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.sync.CommittedReader;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * A reader which sometimes returns erroneous commands.
 */
@Singleton
public final class SometimesByzantineCommittedReader implements CommittedSender, CommittedReader {
	private final TreeMap<Long, VerifiedCommandsAndProof> commandsAndProof = new TreeMap<>();
	private final LedgerAccumulator accumulator;
	private ReadType currentReadType;

	@Inject
	public SometimesByzantineCommittedReader(Random random, LedgerAccumulator accumulator) {
		this.accumulator = Objects.requireNonNull(accumulator);
		this.currentReadType = ReadType.values()[random.nextInt(ReadType.values().length)];
	}

	@Override
	public void sendCommitted(VerifiedCommandsAndProof verifiedCommandsAndProof, BFTValidatorSet validatorSet) {
		commandsAndProof.put(verifiedCommandsAndProof.getFirstVersion(), verifiedCommandsAndProof);
	}

	private enum ReadType {
		GOOD {
			@Override
			VerifiedCommandsAndProof transform(
				DtoLedgerHeaderAndProof request,
				VerifiedCommandsAndProof correctCommands,
				LedgerAccumulator ledgerAccumulator
			) {
				return correctCommands;
			}
		},
		BAD_COMMANDS {
			@Override
			VerifiedCommandsAndProof transform(
				DtoLedgerHeaderAndProof request,
				VerifiedCommandsAndProof correctCommands,
				LedgerAccumulator ledgerAccumulator
			) {
				ImmutableList<Command> badCommands = Stream.generate(() -> new Command(new byte[]{0}))
					.limit(correctCommands.size())
					.collect(ImmutableList.toImmutableList());
				return new VerifiedCommandsAndProof(badCommands, correctCommands.getHeader());
			}
		},
		BAD_SIGNATURES {
			@Override
			VerifiedCommandsAndProof transform(
				DtoLedgerHeaderAndProof request,
				VerifiedCommandsAndProof correctCommands,
				LedgerAccumulator ledgerAccumulator
			) {
				ImmutableList<Command> badCommands = Stream.generate(() -> new Command(new byte[]{0}))
					.limit(correctCommands.size())
					.collect(ImmutableList.toImmutableList());
				Hash accumulated = request.getLedgerHeader().getAccumulator();
				for (Command command : badCommands) {
					accumulated = ledgerAccumulator.accumulate(accumulated, command);
				}
				LedgerHeader ledgerHeader = LedgerHeader.create(
					correctCommands.getHeader().getEpoch(),
					correctCommands.getHeader().getView(),
					correctCommands.getHeader().getStateVersion(),
					accumulated,
					correctCommands.getHeader().timestamp(),
					correctCommands.getHeader().isEndOfEpoch()
				);
				VerifiedLedgerHeaderAndProof headerAndProof = new VerifiedLedgerHeaderAndProof(
					correctCommands.getHeader().toDto().getOpaque0(),
					correctCommands.getHeader().toDto().getOpaque1(),
					correctCommands.getHeader().toDto().getOpaque2(),
					correctCommands.getHeader().toDto().getOpaque3(),
					ledgerHeader,
					new TimestampedECDSASignatures()
				);
				return new VerifiedCommandsAndProof(badCommands, headerAndProof);
			}
		};

		abstract VerifiedCommandsAndProof transform(
			DtoLedgerHeaderAndProof request,
			VerifiedCommandsAndProof correctCommands,
			LedgerAccumulator ledgerAccumulator
		);
	}


	@Override
	public VerifiedCommandsAndProof getNextCommittedCommands(DtoLedgerHeaderAndProof start, int batchSize) {
		final long stateVersion = start.getLedgerHeader().getStateVersion();
		Entry<Long, VerifiedCommandsAndProof> entry = commandsAndProof.higherEntry(stateVersion);
		if (entry != null) {
			VerifiedCommandsAndProof commandsToSendBack = entry.getValue().truncateFromVersion(stateVersion);
			commandsToSendBack = currentReadType.transform(start, commandsToSendBack, accumulator);
			currentReadType = ReadType.values()[(currentReadType.ordinal() + 1) % ReadType.values().length];
			return commandsToSendBack;
		}

		return null;
	}
}
