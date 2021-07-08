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

import com.google.common.hash.HashCode;
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

	static TransactionAction register(ECPublicKey validatorKey) {
		return new RegisterValidatorAction(validatorKey);
	}

	static TransactionAction unregister(ECPublicKey validatorKey) {
		return new UnregisterValidatorAction(validatorKey);
	}

	static TransactionAction update(ECPublicKey validatorKey, Optional<String> name, Optional<String> url, Optional<HashCode> forkVoteHash) {
		return new UpdateValidatorAction(validatorKey, name.orElse(null), url.orElse(null), forkVoteHash);
	}

	static TransactionAction updateValidatorFee(ECPublicKey validatorKey, int percentage) {
		return new UpdateRakeAction(validatorKey, percentage);
	}

	static TransactionAction updateOwnerAddress(ECPublicKey validatorKey, REAddr ownerAddress) {
		return new UpdateValidatorOwnerAddressAction(validatorKey, ownerAddress);
	}

	static TransactionAction updateAllowDelegation(ECPublicKey validatorKey, boolean allowDelegation) {
		return new UpdateAllowDelegationFlagAction(validatorKey, allowDelegation);
	}

	static TransactionAction createFixed(
		REAddr from, ECPublicKey signer, String symbol, String name,
		String description, String iconUrl, String tokenUrl, UInt256 amount
	) {
		return new CreateFixedTokenAction(
			from, amount, REAddr.ofHashedKey(signer, symbol), name,
			symbol, iconUrl, tokenUrl, description
		);
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

	Stream<TxAction> toAction();

	static REAddr rriValue(REAddr rri) {
		return ofNullable(rri).orElseThrow(() -> new IllegalStateException("Attempt to transfer with missing RRI"));
	}
}

