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

package com.radixdlt.client.lib.api;

import com.fasterxml.jackson.annotation.JsonProperty;
//import com.radixdlt.client.lib.dto.Action;
import com.google.common.hash.HashCode;
import com.radixdlt.client.lib.api.action.Action;
import com.radixdlt.client.lib.api.action.BurnAction;
import com.radixdlt.client.lib.api.action.CreateFixedTokenAction;
import com.radixdlt.client.lib.api.action.CreateMutableTokenAction;
import com.radixdlt.client.lib.api.action.MintAction;
import com.radixdlt.client.lib.api.action.RegisterValidatorAction;
import com.radixdlt.client.lib.api.action.StakeAction;
import com.radixdlt.client.lib.api.action.TransferAction;
import com.radixdlt.client.lib.api.action.UnregisterValidatorAction;
import com.radixdlt.client.lib.api.action.UnstakeAction;
import com.radixdlt.client.lib.api.action.UpdateValidatorAction;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.UInt256;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TransactionRequest {
	private final List<Action> actions;
	private final String message;

	private TransactionRequest(String message, List<Action> actions) {
		this.message = message;
		this.actions = actions;
	}

	public static TransactionRequestBuilder createBuilder() {
		return new TransactionRequestBuilder();
	}

	@JsonProperty("message")
	public String getMessage() {
		return message;
	}

	@JsonProperty("actions")
	public List<Action> getActions() {
		return actions;
	}

	public static final class TransactionRequestBuilder {
		private final List<Action> actions = new ArrayList<>();
		private String message;

		private TransactionRequestBuilder() { }

		public TransactionRequestBuilder transfer(AccountAddress from, AccountAddress to, UInt256 amount, String rri) {
			actions.add(new TransferAction(from, to, amount, rri));
			return this;
		}

		public TransactionRequestBuilder stake(AccountAddress from, ValidatorAddress validator, UInt256 amount) {
			actions.add(new StakeAction(from, validator, amount));
			return this;
		}

		public TransactionRequestBuilder unstake(AccountAddress from, ValidatorAddress validator, UInt256 amount) {
			actions.add(new UnstakeAction(from, validator, amount));
			return this;
		}

		public TransactionRequestBuilder burn(AccountAddress from, UInt256 amount, String rri) {
			actions.add(new BurnAction(from, amount, rri));
			return this;
		}

		public TransactionRequestBuilder mint(AccountAddress from, UInt256 amount, String rri) {
			actions.add(new MintAction(from, amount, rri));
			return this;
		}

		public TransactionRequestBuilder registerValidator(ValidatorAddress delegate, Optional<String> name, Optional<String> url) {
			actions.add(new RegisterValidatorAction(delegate, name.orElse(null), url.orElse(null)));
			return this;
		}

		public TransactionRequestBuilder unregisterValidator(ValidatorAddress delegate, Optional<String> name, Optional<String> url) {
			actions.add(new UnregisterValidatorAction(delegate, name.orElse(null), url.orElse(null)));
			return this;
		}

		public TransactionRequestBuilder updateValidator(
			ValidatorAddress delegate,
			Optional<String> name,
			Optional<String> url,
			Optional<HashCode> forkVoteHash
		) {
			actions.add(new UpdateValidatorAction(delegate, name.orElse(null), url.orElse(null), forkVoteHash.orElse(null)));
			return this;
		}

		public TransactionRequestBuilder createFixed(
			AccountAddress from, ECPublicKey signer, String rri, String symbol, String name,
			String description, String iconUrl, String tokenUrl, UInt256 amount
		) {
			actions.add(new CreateFixedTokenAction(from, signer, amount, rri, name, symbol, iconUrl, tokenUrl, description));
			return this;
		}

		public TransactionRequestBuilder createMutable(
			ECPublicKey signer, String symbol, String name,
			Optional<String> description, Optional<String> iconUrl, Optional<String> tokenUrl
		) {
			new CreateMutableTokenAction(
				signer, name, symbol,
				iconUrl.orElse(null), tokenUrl.orElse(null), description.orElse(null)
			);
			return this;
		}

		public TransactionRequestBuilder message(String message) {
			this.message = message;
			return this;
		}

		public TransactionRequest build() {
			return new TransactionRequest(message, actions);
		}
	}
}
