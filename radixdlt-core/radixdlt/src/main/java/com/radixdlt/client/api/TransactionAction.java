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
import com.radixdlt.atom.actions.IncludeMessage;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Functions.FN5;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class TransactionAction {
	private final ActionType actionType;
	private final ECPublicKey from;
	private final ECPublicKey to;
	private final UInt256 amount;
	private final Optional<REAddr> rri;
	private final byte[] data;

	private TransactionAction(
		ActionType actionType,
		ECPublicKey from,
		ECPublicKey to,
		UInt256 amount,
		Optional<REAddr> rri,
		byte[] data
	) {
		this.actionType = actionType;
		this.from = from;
		this.to = to;
		this.amount = amount;
		this.rri = rri;
		this.data = data;
	}

	public static TransactionAction msg(String msg) {
		return new TransactionAction(ActionType.MSG, null, null, null, null, msg.getBytes(StandardCharsets.UTF_8));
	}

	public static TransactionAction create(
		ActionType actionType,
		ECPublicKey from,
		ECPublicKey to,
		UInt256 amount,
		Optional<REAddr> rri
	) {
		return new TransactionAction(actionType, from, to, amount, rri, null);
	}

	public static TransactionAction create(
		ActionType actionType,
		ECPublicKey from,
		ECPublicKey to,
		UInt256 amount,
		Optional<REAddr> rri,
		byte[] data
	) {
		return new TransactionAction(actionType, from, to, amount, rri, data);
	}

	public ECPublicKey getFrom() {
		return from;
	}

	public <T> T map(FN5<T, ActionType, ECPublicKey, ECPublicKey, UInt256, Optional<REAddr>> mapper) {
		return mapper.apply(actionType, from, to, amount, rri);
	}

	public TxAction toAction(byte magic) {
		switch (actionType) {
			case MSG:
				return new IncludeMessage(data);
			case TRANSFER:
				var addr = new RadixAddress(magic, to);
				return new TransferToken(rriValue(), addr, amount);
			case STAKE:
				return new StakeTokens(to, amount);
			case UNSTAKE:
				return new UnstakeTokens(to, amount);
		}
		throw new IllegalStateException("Unsupported action type " + actionType);
	}

	private REAddr rriValue() {
		return rri.orElseThrow(() -> new IllegalStateException("Attempt to transfer with missing RRI"));
	}
}
