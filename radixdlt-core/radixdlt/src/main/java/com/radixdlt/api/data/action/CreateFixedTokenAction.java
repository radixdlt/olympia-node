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

package com.radixdlt.api.data.action;

import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.actions.CreateFixedToken;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.stream.Stream;

class CreateFixedTokenAction implements TransactionAction {
	private final REAddr from;
	private final UInt256 amount;
	private final REAddr rri;
	private final String name;
	private final String symbol;
	private final String iconUrl;
	private final String tokenUrl;
	private final String description;

	CreateFixedTokenAction(
		REAddr from,
		UInt256 amount,
		REAddr rri,
		String name,
		String symbol,
		String iconUrl,
		String tokenUrl,
		String description
	) {
		this.from = from;
		this.amount = amount;
		this.rri = rri;
		this.name = name;
		this.symbol = symbol;
		this.iconUrl = iconUrl;
		this.tokenUrl = tokenUrl;
		this.description = description;
	}

	@Override
	public REAddr getFrom() {
		return from;
	}

	@Override
	public Stream<TxAction> toAction() {
		return Stream.of(new CreateFixedToken(TransactionAction.rriValue(rri), from, symbol, name, description, iconUrl, tokenUrl, amount));
	}
}
