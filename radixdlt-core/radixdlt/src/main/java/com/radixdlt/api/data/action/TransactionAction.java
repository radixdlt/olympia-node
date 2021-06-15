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
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

public interface TransactionAction {
	static TransactionAction transfer(REAddr from, REAddr to, UInt256 amount, REAddr rri) {
		return new TransferAction(from, to, amount, rri);
	}

	static TransactionAction stake(REAddr from, ECPublicKey delegate, UInt256 amount) {
		return new StakeAction(from, delegate, amount);
	}

	static TransactionAction unstake(REAddr from, ECPublicKey delegate, UInt256 amount) {
		return new UnstakeAction(from, delegate, amount);
	}

	static TransactionAction burn(REAddr from, REAddr rri, UInt256 amount) {
		return new BurnAction(from, amount, rri);
	}

	static TransactionAction mint(REAddr to, REAddr rri, UInt256 amount) {
		return new MintAction(to, amount, rri);
	}

	static TransactionAction register(ECPublicKey delegate, Optional<String> name, Optional<String> url) {
		return new RegisterValidatorAction(delegate, name.orElse(null), url.orElse(null));
	}

	static TransactionAction unregister(ECPublicKey delegate, Optional<String> name, Optional<String> url) {
		return new UnregisterValidatorAction(delegate, name.orElse(null), url.orElse(null));
	}

	static TransactionAction update(ECPublicKey delegate, Optional<String> name, Optional<String> url) {
		return new UpdateValidatorAction(delegate, name.orElse(null), url.orElse(null));
	}

	static TransactionAction createFixed(
		REAddr from, REAddr rri, String symbol, String name,
		String description, String iconUrl, String tokenUrl, UInt256 amount
	) {
		return new CreateFixedTokenAction(from, amount, rri, name, symbol, iconUrl, tokenUrl, description);
	}

	static TransactionAction createMutable(
		ECPublicKey signer, String symbol, String name,
		Optional<String> description, Optional<String> iconUrl, Optional<String> tokenUrl
	) {
		return new CreateMutableTokenAction(
			signer, name, symbol,
			iconUrl.orElse(null), tokenUrl.orElse(null), description.orElse(null)
		);
	}

	REAddr getFrom();

	Stream<TxAction> toAction();

	static REAddr rriValue(REAddr rri) {
		return ofNullable(rri).orElseThrow(() -> new IllegalStateException("Attempt to transfer with missing RRI"));
	}
}
