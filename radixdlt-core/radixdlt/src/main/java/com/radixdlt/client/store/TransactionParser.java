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

import com.radixdlt.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.atommodel.validators.ValidatorParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TransactionParser {
	private TransactionParser() { }

	public static Result<TxHistoryEntry> parse(RRI nativeToken, RadixAddress owner, ParsedTx parsedTx, Instant txDate) {
		return new ParsingContext(parsedTx.getParticles(), parsedTx.getMessage(), parsedTx.getId(), txDate, nativeToken, owner)
			.parse();
	}

	private static class ParsingContext {
		private static final ActionEntry UNKNOWN_ACTION = ActionEntry.unknown();

		private final List<ParticleWithSpin> input;
		private final Optional<String> message;
		private final AID txId;
		private final Instant txDate;
		private final RRI nativeToken;
		private final RadixAddress owner;
		private final List<ActionEntry> actions = new ArrayList<>();

		private int pos;
		private UInt256 fee = UInt256.ZERO;

		ParsingContext(List<ParticleWithSpin> input, Optional<String> message, AID txId, Instant txDate, RRI nativeToken,
					   RadixAddress owner) {
			this.input = input;
			this.message = message;
			this.txId = txId;
			this.txDate = txDate;
			this.nativeToken = nativeToken;
			this.owner = owner;
		}

		public Result<TxHistoryEntry> parse() {
			parseActions();

			return Result.ok(TxHistoryEntry.create(
				txId,
				txDate,
				fee,
				//TODO: remove!!!
				message.flatMap(MessageEntry::fromPlainString).orElse(null),
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
			if (current() instanceof ValidatorParticle && isUp()) {
				pos++;

				if (current() instanceof ValidatorParticle && isDown()) {
					pos++;
				}
			}
		}

		private void parseUnregisterValidator() {
			if (current() instanceof ValidatorParticle && isDown()) {
				pos++;

				if (current() instanceof ValidatorParticle && isUp()) {
					pos++;
				}
			}
		}

		private void parseMint() {
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
			if (current() instanceof StakedTokensParticle && isUp()) {
				var stake = (StakedTokensParticle) current();
				pos++;

				if (current() instanceof TransferrableTokensParticle && isDown()) {
					pos++;
				}

				parseRemainder();

				actions.add(ActionEntry.fromStake(stake, nativeToken));
			}
		}

		private void parseUnStake() {
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

				actions.add(ActionEntry.fromUnstake(unstake, nativeToken));
			}
		}

		private void parseTransfer() {
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
			return pos >= input.size() ? null : input.get(pos).getParticle();
		}

		private boolean isUp() {
			return input.get(pos).getSpin() == Spin.UP;
		}

		private boolean isDown() {
			return input.get(pos).getSpin() == Spin.DOWN;
		}
	}
}
