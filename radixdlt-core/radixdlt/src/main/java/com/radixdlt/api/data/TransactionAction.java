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

package com.radixdlt.api.data;

import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.atom.actions.CreateFixedToken;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.UnregisterValidator;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.atom.actions.UpdateValidator;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

public class TransactionAction {
	private final ActionType actionType;
	private final REAddr from;
	private final REAddr to;
	private final ECPublicKey delegate;
	private final UInt256 amount;
	private final REAddr rri;
	private final String name;
	private final String url;
	private final String symbol;
	private final String iconUrl;
	private final String tokenUrl;
	private final String description;

	private TransactionAction(
		ActionType actionType,
		REAddr from,
		REAddr to,
		ECPublicKey delegate,
		UInt256 amount,
		REAddr rri,
		String name,
		String url,
		String symbol,
		String iconUrl,
		String tokenUrl,
		String description
	) {
		this.actionType = actionType;
		this.from = from;
		this.to = to;
		this.amount = amount;
		this.delegate = delegate;
		this.rri = rri;
		this.name = name;
		this.url = url;
		this.symbol = symbol;
		this.iconUrl = iconUrl;
		this.tokenUrl = tokenUrl;
		this.description = description;
	}

	public static TransactionAction transfer(REAddr from, REAddr to, UInt256 amount, REAddr rri) {
		return create(
			ActionType.TRANSFER, from, to, null, amount, rri,
			null, null, null, null, null, null
		);
	}

	public static TransactionAction stake(REAddr from, ECPublicKey delegate, UInt256 amount) {
		return create(
			ActionType.STAKE, from, null, delegate, amount, null,
			null, null, null, null, null, null
		);
	}

	public static TransactionAction unstake(REAddr from, ECPublicKey delegate, UInt256 amount) {
		return create(
			ActionType.UNSTAKE, from, null, delegate, amount, null,
			null, null, null, null, null, null
		);
	}

	public static TransactionAction burn(REAddr from, REAddr rri, UInt256 amount) {
		return create(
			ActionType.BURN, from, null, null, amount, rri,
			null, null, null, null, null, null
		);
	}

	public static TransactionAction mint(REAddr to, REAddr rri, UInt256 amount) {
		return create(
			ActionType.MINT, null, to, null, amount, rri,
			null, null, null, null, null, null
		);
	}

	public static TransactionAction register(ECPublicKey delegate, Optional<String> name, Optional<String> url) {
		return create(
			ActionType.REGISTER_VALIDATOR, null, null, delegate, null, null,
			name.orElse(null), url.orElse(null), null, null, null, null
		);
	}

	public static TransactionAction unregister(ECPublicKey delegate, Optional<String> name, Optional<String> url) {
		return create(
			ActionType.UNREGISTER_VALIDATOR, null, null, delegate, null, null,
			name.orElse(null), url.orElse(null), null, null, null, null
		);
	}

	public static TransactionAction update(ECPublicKey delegate, Optional<String> name, Optional<String> url) {
		return create(
			ActionType.UPDATE_VALIDATOR, null, null, delegate, null, null,
			name.orElse(null), url.orElse(null), null, null, null, null
		);
	}

	public static TransactionAction createFixed(
		REAddr from, REAddr rri, String symbol, String name,
		String description, String iconUrl, String tokenUrl, UInt256 amount
	) {
		return create(
			ActionType.CREATE_FIXED, from, null, null, amount, rri,
			name, null, symbol, iconUrl, tokenUrl, description
		);
	}

	public static TransactionAction createMutable(
		String symbol, String name, Optional<String> description, Optional<String> iconUrl, Optional<String> tokenUrl
	) {
		return create(
			ActionType.CREATE_MUTABLE, null, null, null, null, null,
			name, null, symbol, iconUrl.orElse(null), tokenUrl.orElse(null), description.orElse(null)
		);
	}

	private static TransactionAction create(
		ActionType actionType, REAddr from, REAddr to, ECPublicKey delegate, UInt256 amount, REAddr rri,
		String name, String url, String symbol, String iconUrl, String tokenUrl, String description
	) {
		return new TransactionAction(actionType, from, to, delegate, amount, rri, name, url, symbol, iconUrl, tokenUrl, description);
	}

	public REAddr getFrom() {
		return from;
	}

	public Stream<TxAction> toAction() {
		switch (actionType) {
			case MSG:
				return Stream.empty();
			case TRANSFER:
				return Stream.of(new TransferToken(rriValue(), from, to, amount));
			case STAKE:
				return Stream.of(new StakeTokens(from, delegate, amount));
			case UNSTAKE:
				return Stream.of(new UnstakeTokens(from, delegate, amount));
			case BURN:
				return Stream.of(new BurnToken(rriValue(), from, amount));
			case MINT:
				return Stream.of(new MintToken(rriValue(), to, amount));
			case REGISTER_VALIDATOR:
				return Stream.of(new RegisterValidator(delegate, name, url));
			case UNREGISTER_VALIDATOR:
				return Stream.of(new UnregisterValidator(delegate, name, url));
			case UPDATE_VALIDATOR:
				return Stream.of(new UpdateValidator(delegate, name, url));
			case CREATE_FIXED:
				return Stream.of(new CreateFixedToken(rriValue(), from, symbol, name, description, iconUrl, tokenUrl, amount));
			case CREATE_MUTABLE:
				return Stream.of(new CreateMutableToken(symbol, name, description, iconUrl, tokenUrl));
		}
		throw new IllegalStateException("Unsupported action type " + actionType);
	}

	private REAddr rriValue() {
		return ofNullable(rri).orElseThrow(() -> new IllegalStateException("Attempt to transfer with missing RRI"));
	}
}
