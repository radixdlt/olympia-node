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

package com.radixdlt.sync;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.ledger.VerifiedCommandsAndProof;

import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.UnaryOperator;

/**
 * A reader which sometimes returns erroneous commands.
 */
public final class SometimesByzantineCommittedReader implements CommittedReader {
	private final InMemoryCommittedReader correctReader;
	private final LedgerAccumulator accumulator;
	private final Hasher hasher;
	private ReadType currentReadType;

	@Inject
	public SometimesByzantineCommittedReader(Random random, LedgerAccumulator accumulator, InMemoryCommittedReader correctReader, Hasher hasher) {
		this.correctReader = Objects.requireNonNull(correctReader);
		this.accumulator = Objects.requireNonNull(accumulator);
		this.currentReadType = ReadType.values()[random.nextInt(ReadType.values().length)];
		this.hasher = hasher;
	}

	public EventProcessor<LedgerUpdate> ledgerUpdateEventProcessor() {
		return this.correctReader.updateProcessor();
	}

	private static class ByzantineVerifiedCommandsAndProofBuilder {
		private DtoLedgerHeaderAndProof request;
		private UnaryOperator<Command> commandMapper;
		private VerifiedCommandsAndProof base;
		private TimestampedECDSASignatures overwriteSignatures;
		private LedgerAccumulator accumulator;
		private Hasher hasher;

		public ByzantineVerifiedCommandsAndProofBuilder hasher(Hasher hasher) {
			this.hasher = hasher;
			return this;
		}

		public ByzantineVerifiedCommandsAndProofBuilder accumulator(DtoLedgerHeaderAndProof request, LedgerAccumulator accumulator) {
			this.request = request;
			this.accumulator = accumulator;
			return this;
		}

		public ByzantineVerifiedCommandsAndProofBuilder base(VerifiedCommandsAndProof base) {
			this.base = base;
			return this;
		}

		public ByzantineVerifiedCommandsAndProofBuilder replaceCommands(UnaryOperator<Command> commandMapper) {
			this.commandMapper = commandMapper;
			return this;
		}

		public ByzantineVerifiedCommandsAndProofBuilder overwriteSignatures(TimestampedECDSASignatures overwriteSignatures) {
			this.overwriteSignatures = overwriteSignatures;
			return this;
		}

		public VerifiedCommandsAndProof build() {
			ImmutableList<Command> commands;
			if (commandMapper != null) {
				commands = base.getCommands().stream()
					.map(commandMapper)
					.collect(ImmutableList.toImmutableList());
			} else {
				commands = base.getCommands();
			}

			AccumulatorState accumulatorState;
			if (accumulator != null) {
				accumulatorState = request.getLedgerHeader().getAccumulatorState();
				for (Command command : commands) {
					accumulatorState = accumulator.accumulate(accumulatorState, hasher.hash(command));
				}
			} else {
				accumulatorState = base.getHeader().getAccumulatorState();
			}

			LedgerHeader ledgerHeader = LedgerHeader.create(
				base.getHeader().getEpoch(),
				base.getHeader().getView(),
				accumulatorState,
				base.getHeader().timestamp(),
				base.getHeader().getNextValidatorSet().orElse(null)
			);
			TimestampedECDSASignatures signatures = overwriteSignatures != null ? overwriteSignatures : base.getHeader().getSignatures();
			VerifiedLedgerHeaderAndProof headerAndProof = new VerifiedLedgerHeaderAndProof(
				base.getHeader().toDto().getOpaque0(),
				base.getHeader().toDto().getOpaque1(),
				base.getHeader().toDto().getOpaque2(),
				base.getHeader().toDto().getOpaque3(),
				ledgerHeader,
				signatures
			);

			return new VerifiedCommandsAndProof(commands, headerAndProof);
		}
	}

	private enum ReadType {
		GOOD {
			@Override
			VerifiedCommandsAndProof transform(
				DtoLedgerHeaderAndProof request,
				VerifiedCommandsAndProof correctCommands,
				LedgerAccumulator ledgerAccumulator,
				Hasher hasher
			) {
				return correctCommands;
			}
		},
		BAD_COMMANDS {
			@Override
			VerifiedCommandsAndProof transform(
				DtoLedgerHeaderAndProof request,
				VerifiedCommandsAndProof correctCommands,
				LedgerAccumulator ledgerAccumulator,
				Hasher hasher
			) {
				return new ByzantineVerifiedCommandsAndProofBuilder()
					.hasher(hasher)
					.base(correctCommands)
					.replaceCommands(cmd -> new Command(new byte[]{0}))
					.build();
			}
		},
		NO_SIGNATURES {
			@Override
			VerifiedCommandsAndProof transform(
				DtoLedgerHeaderAndProof request,
				VerifiedCommandsAndProof correctCommands,
				LedgerAccumulator accumulator,
				Hasher hasher
			) {
				return new ByzantineVerifiedCommandsAndProofBuilder()
					.hasher(hasher)
					.base(correctCommands)
					.replaceCommands(cmd -> new Command(new byte[]{0}))
					.accumulator(request, accumulator)
					.overwriteSignatures(new TimestampedECDSASignatures())
					.build();
			}
		},
		BAD_SIGNATURES {
			@Override
			VerifiedCommandsAndProof transform(
				DtoLedgerHeaderAndProof request,
				VerifiedCommandsAndProof correctCommands,
				LedgerAccumulator accumulator,
				Hasher hasher
			) {
				return new ByzantineVerifiedCommandsAndProofBuilder()
					.hasher(hasher)
					.base(correctCommands)
					.replaceCommands(cmd -> new Command(new byte[]{0}))
					.accumulator(request, accumulator)
					.build();
			}
		};

		abstract VerifiedCommandsAndProof transform(
			DtoLedgerHeaderAndProof request,
			VerifiedCommandsAndProof correctCommands,
			LedgerAccumulator ledgerAccumulator,
			Hasher hasher
		);
	}

	@Override
	public VerifiedCommandsAndProof getNextCommittedCommands(DtoLedgerHeaderAndProof start) {
		VerifiedCommandsAndProof correctResult = correctReader.getNextCommittedCommands(start);
		// TODO: Make epoch sync byzantine as well
		if (start.getLedgerHeader().isEndOfEpoch()) {
			return correctResult;
		}

		if (correctResult != null) {
			currentReadType = ReadType.values()[(currentReadType.ordinal() + 1) % ReadType.values().length];
			return currentReadType.transform(start, correctResult, accumulator, hasher);
		}

		return null;
	}

	@Override
	public Optional<VerifiedLedgerHeaderAndProof> getEpochVerifiedHeader(long epoch) {
		return correctReader.getEpochVerifiedHeader(epoch);
	}
}
