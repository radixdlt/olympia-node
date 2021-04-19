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

package com.radixdlt.client.api;

import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.identifiers.Rri;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Functions.FN5;

import java.util.Optional;

public class TransactionAction {
	private final ActionType actionType;
	private final RadixAddress from;
	private final RadixAddress to;
	private final UInt256 amount;
	private final Optional<Rri> rri;

	private TransactionAction(
		ActionType actionType,
		RadixAddress from,
		RadixAddress to,
		UInt256 amount,
		Optional<Rri> rri
	) {
		this.actionType = actionType;
		this.from = from;
		this.to = to;
		this.amount = amount;
		this.rri = rri;
	}

	public static TransactionAction create(
		ActionType actionType,
		RadixAddress from,
		RadixAddress to,
		UInt256 amount,
		Optional<Rri> rri
	) {
		return new TransactionAction(actionType, from, to, amount, rri);
	}

	public RadixAddress getFrom() {
		return from;
	}

	public <T> T map(FN5<T, ActionType, RadixAddress, RadixAddress, UInt256, Optional<Rri>> mapper) {
		return mapper.apply(actionType, from, to, amount, rri);
	}

	public TxAction toAction() {
		switch (actionType) {
			case TRANSFER:
				return new TransferToken(rriValue(), to, amount);
			case STAKE:
				return new StakeTokens(to.getPublicKey(), amount);
			case UNSTAKE:
				return new UnstakeTokens(to.getPublicKey(), amount);
		}
		throw new IllegalStateException("Unsupported action type " + actionType);
	}

	private Rri rriValue() {
		return rri.orElseThrow(() -> new IllegalStateException("Attempt to transfer with missing RRI"));
	}
}
