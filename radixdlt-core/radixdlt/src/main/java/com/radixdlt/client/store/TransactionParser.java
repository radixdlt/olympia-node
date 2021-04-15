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

import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.constraintmachine.REParsedAction;
import com.radixdlt.constraintmachine.REParsedTxn;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;
import org.radix.api.jsonrpc.ActionType;

import java.time.Instant;
import java.util.stream.Collectors;

public final class TransactionParser {
	private final RRI nativeToken;

	public TransactionParser(RRI nativeToken) {
		this.nativeToken = nativeToken;
	}

	private UInt256 computeFeePaid(REParsedTxn radixEngineTxn) {
		return radixEngineTxn.getActions()
			.stream()
			.map(REParsedAction::getTxAction)
			.filter(BurnToken.class::isInstance)
			.map(BurnToken.class::cast)
			.filter(t -> t.rri().equals(nativeToken))
			.map(BurnToken::amount)
			.reduce(UInt256::add)
			.orElse(UInt256.ZERO);
	}

	private ActionEntry mapToEntry(RadixAddress user, TxAction txAction) {
		if (txAction instanceof TransferToken) {
			var transferToken = (TransferToken) txAction;
			return ActionEntry.create(ActionType.TRANSFER, user, transferToken.to(), transferToken.amount(), transferToken.rri());
		} else if (txAction instanceof BurnToken) {
			var burnToken = (BurnToken) txAction;
			return ActionEntry.create(ActionType.BURN, user, null, burnToken.amount(), burnToken.rri());
		} else if (txAction instanceof StakeTokens) {
			var stakeToken = (StakeTokens) txAction;
			return ActionEntry.create(ActionType.STAKE, user, stakeToken.to(), stakeToken.amount(), nativeToken);
		} else if (txAction instanceof UnstakeTokens) {
			var unstakeToken = (UnstakeTokens) txAction;
			return ActionEntry.create(ActionType.UNSTAKE, unstakeToken.from(), user, unstakeToken.amount(), nativeToken);
		} else {
			return ActionEntry.unknown();
		}
	}

	public Result<TxHistoryEntry> parse(REParsedTxn parsedTxn, Instant txDate) {
		var txnId = parsedTxn.getTxn().getId();
		var fee = computeFeePaid(parsedTxn);

		var actions = parsedTxn.getActions().stream()
			.map(a -> mapToEntry(parsedTxn.getUser(), a.getTxAction()))
			.collect(Collectors.toList());

		return Result.ok(TxHistoryEntry.create(txnId, txDate, fee, null, actions));
	}
}
