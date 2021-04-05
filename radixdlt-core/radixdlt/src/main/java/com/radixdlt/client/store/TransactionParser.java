/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.client.store;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.radixdlt.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.atommodel.validators.UnregisteredValidatorParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.constraintmachine.ParsedInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.berkeley.FullTransaction;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Failure;
import com.radixdlt.utils.functional.Result;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.radixdlt.serialization.SerializationUtils.restore;

public class TransactionParser {
	private static final Logger log = LogManager.getLogger();

	private final Serialization serialization;

	@Inject
	public TransactionParser(Serialization serialization) {
		this.serialization = serialization;
	}

	public Result<TxHistoryEntry> parse(RadixAddress owner, FullTransaction txWithId, Instant txDate) {
		var instructions = txWithId.getTx()
			.uniqueInstructions()
			.map(i -> restore(serialization, i.getData(), Particle.class)
				.map(substate -> ParsedInstruction.of(substate, i.getNextSpin())))
			.peek(instruction -> instruction.onFailure(this::reportError))
			.filter(Result::isSuccess)
			.map(p -> p.fold(this::shouldNeverHappen, v -> v))
			.collect(Collectors.toList());

		return new ParsingContext(instructions, txWithId.getTx().getMessage(), txWithId.getTxId(), txDate, owner)
			.parse();
	}

	private static class ParsingContext {
		private static final ActionEntry UNKNOWN_ACTION = ActionEntry.unknown();

		private final List<ParsedInstruction> input;
		private final String message;
		private final AID txId;
		private final Instant txDate;
		private final RadixAddress owner;
		private final List<ActionEntry> actions = new ArrayList<>();

		private int pos;
		private UInt256 fee = UInt256.ZERO;

		ParsingContext(List<ParsedInstruction> input, String message, AID txId, Instant txDate, RadixAddress owner) {
			this.input = input;
			this.message = message;
			this.txId = txId;
			this.txDate = txDate;
			this.owner = owner;
		}

		public Result<TxHistoryEntry> parse() {
			parseActions();

			return Result.ok(TxHistoryEntry.create(
				txId,
				txDate,
				fee,
				//TODO: add support for encrypted messages
				MessageEntry.fromPlainString(message).orElse(null),
				actions
			));
		}

		private void parseActions() {
			while (pos < input.size()) {
				var savedPos = pos;

				parseTokenDefinition();
				parseRegisterValidator();
				parseUnregisterValidator();
				parseMint();
				parseStake();
				parseUnStake();
				parseTransfer();
				parseFeeBurn();

				if (savedPos == pos) {
					// Unable to decode actions, abort parsing
					if (actions.isEmpty()) {
						actions.add(UNKNOWN_ACTION);
					}
					break;
				}
			}
		}

		private void parseTokenDefinition() {
			if (pos >= input.size()) {
				return;
			}

			if (!(current() instanceof RRIParticle)) {
				return;
			}

			pos++;

			if (current() instanceof UnallocatedTokensParticle) {
				pos++;
			}

			if (current() instanceof MutableSupplyTokenDefinitionParticle
				|| current() instanceof FixedSupplyTokenDefinitionParticle) {
				pos++;
			}
		}

		private void parseRegisterValidator() {
			if (pos >= input.size()) {
				return;
			}

			if (current() instanceof UnregisteredValidatorParticle && isUp()) {
				pos++;

				if (current() instanceof RegisteredValidatorParticle && isDown()) {
					pos++;
				}
			}
		}

		private void parseUnregisterValidator() {
			if (pos >= input.size()) {
				return;
			}

			if (current() instanceof RegisteredValidatorParticle && isDown()) {
				pos++;

				if (current() instanceof RegisteredValidatorParticle && isUp()) {
					pos++;
				}
			}
		}

		private void parseMint() {
			if (pos >= input.size()) {
				return;
			}

			if (current() instanceof TransferrableTokensParticle) {
				pos++;

				if (!(current() instanceof UnallocatedTokensParticle)) {
					pos--;
					return;
				}

				pos++;

				if (current() instanceof UnallocatedTokensParticle) {
					// remainder transfer, optional
					pos++;
				}
			}
		}

		private void parseStake() {
			if (pos >= input.size()) {
				return;
			}

			if (current() instanceof StakedTokensParticle && isUp()) {
				var stake = (StakedTokensParticle) current();
				pos++;

				if (current() instanceof TransferrableTokensParticle && isDown()) {
					pos++;
				}

				parseRemainder();

				actions.add(ActionEntry.fromStake(stake));
			}
		}

		private void parseUnStake() {
			if (pos >= input.size()) {
				return;
			}

			if (current() instanceof TransferrableTokensParticle && isUp()) {
				pos++;

				if (!(current() instanceof StakedTokensParticle)) {
					pos--;
					return;
				}

				var unstake = (StakedTokensParticle) current();
				pos++;

				if (current() instanceof StakedTokensParticle && isUp()) {
					// remainder transfer, optional
					pos++;
				}

				actions.add(ActionEntry.fromUnstake(unstake));
			}
		}

		private void parseTransfer() {
			if (pos >= input.size()) {
				return;
			}

			if (current() instanceof TransferrableTokensParticle && isUp()) {
				var transfer = (TransferrableTokensParticle) current();
				pos++;

				if (current() instanceof TransferrableTokensParticle && isDown()) {
					pos++;
				}

				parseRemainder();

				actions.add(ActionEntry.transfer(transfer, owner));
			}
		}

		private void parseFeeBurn() {
			if (pos >= input.size()) {
				return;
			}

			if (current() instanceof UnallocatedTokensParticle) {
				var feeParticle = (UnallocatedTokensParticle) current();
				pos++;

				if (current() instanceof TransferrableTokensParticle && isDown()) {
					pos++;
				}

				parseRemainder();

				fee = fee.add(feeParticle.getAmount());
			}
		}

		private void parseRemainder() {
			if (pos >= input.size()) {
				return;
			}

			if (current() instanceof TransferrableTokensParticle && isUp()) {
				//This can be remainder or beginning if the next transfer/unstake
				var remainder = (TransferrableTokensParticle) current();

				// Transfer to self is the beginning of unstake, make sure this is not the case
				if (remainder.getAddress().equals(owner)) {
					pos++;

					if (pos < input.size() && current() instanceof StakedTokensParticle) {
						pos--;
					}
				}
			}
		}

		private Particle current() {
			return input.get(pos).getParticle();
		}

		private boolean isUp() {
			return input.get(pos).getSpin() == Spin.UP;
		}

		private boolean isDown() {
			return input.get(pos).getSpin() == Spin.DOWN;
		}
	}

	private void reportError(Failure failure) {
		log.error(failure.message());
	}

	private <T> T shouldNeverHappen(Failure f) {
		log.error("Should never happen {}", f.message());
		return null;
	}
}
