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
import com.radixdlt.client.lib.api.action.UpdateValidatorAllowDelegationFlagAction;
import com.radixdlt.client.lib.api.action.UpdateValidatorFeeAction;
import com.radixdlt.client.lib.api.action.UpdateValidatorOwnerAction;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.UInt256;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TransactionRequest {
	private final List<Action> actions;
	private final String message;
	private final AccountAddress feePayer;
	private final Boolean disableResourceAllocationAndDestroy;

	private TransactionRequest(
		String message, List<Action> actions, AccountAddress feePayer, Boolean disableResourceAllocationAndDestroy
	) {
		this.message = message;
		this.actions = actions;
		this.feePayer = feePayer;
		this.disableResourceAllocationAndDestroy = disableResourceAllocationAndDestroy;
	}

	public static TransactionRequestBuilder createBuilder(AccountAddress feePayer) {
		return new TransactionRequestBuilder(feePayer);
	}

	@JsonProperty("message")
	public String getMessage() {
		return message;
	}

	@JsonProperty("actions")
	public List<Action> getActions() {
		return actions;
	}

	@JsonProperty("feePayer")
	public AccountAddress getFeePayer() {
		return feePayer;
	}

	@JsonProperty("disableResourceAllocationAndDestroy")
	public Boolean disableResourceAllocationAndDestroy() {
		return disableResourceAllocationAndDestroy;
	}

	public static final class TransactionRequestBuilder {
		private final List<Action> actions = new ArrayList<>();
		private final AccountAddress feePayer;
		private String message;
		private Boolean disableResourceAllocationAndDestroy;

		private TransactionRequestBuilder(AccountAddress feePayer) {
			this.feePayer = feePayer;
		}

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

		public TransactionRequestBuilder registerValidator(ValidatorAddress validator, Optional<String> name, Optional<String> url) {
			actions.add(new RegisterValidatorAction(validator, name.orElse(null), url.orElse(null)));
			return this;
		}

		public TransactionRequestBuilder unregisterValidator(ValidatorAddress validator, Optional<String> name, Optional<String> url) {
			actions.add(new UnregisterValidatorAction(validator, name.orElse(null), url.orElse(null)));
			return this;
		}

		public TransactionRequestBuilder updateValidator(
			ValidatorAddress validator,
			Optional<String> name,
			Optional<String> url,
			Optional<HashCode> forkVoteHash
		) {
			actions.add(new UpdateValidatorAction(validator, name.orElse(null), url.orElse(null), forkVoteHash.orElse(null)));
			return this;
		}

		public TransactionRequestBuilder updateValidatorAllowDelegationFlag(ValidatorAddress validator, boolean allowDelegation) {
			actions.add(new UpdateValidatorAllowDelegationFlagAction(validator, allowDelegation));
			return this;
		}

		public TransactionRequestBuilder updateValidatorFee(ValidatorAddress validator, double validatorFee) {
			actions.add(new UpdateValidatorFeeAction(validator, validatorFee));
			return this;
		}

		public TransactionRequestBuilder updateValidatorOwner(ValidatorAddress validator, AccountAddress owner) {
			actions.add(new UpdateValidatorOwnerAction(validator, owner));
			return this;
		}

		public TransactionRequestBuilder createFixed(
			AccountAddress to, ECPublicKey publicKeyOfSigner, String symbol,
			String name, String description, String iconUrl, String tokenUrl, UInt256 supply
		) {
			actions.add(new CreateFixedTokenAction(to, publicKeyOfSigner, symbol, name, description, iconUrl, tokenUrl, supply));
			return this;
		}

		public TransactionRequestBuilder createMutable(
			ECPublicKey publicKeyOfSigner, String symbol, String name,
			Optional<String> description, Optional<String> iconUrl, Optional<String> tokenUrl
		) {
			actions.add(new CreateMutableTokenAction(
				publicKeyOfSigner, symbol, name,
				iconUrl.orElse(null), tokenUrl.orElse(null), description.orElse(null)
			));
			return this;
		}

		public TransactionRequestBuilder message(String message) {
			this.message = message;
			return this;
		}

		public TransactionRequestBuilder disableResourceAllocationAndDestroy() {
			this.disableResourceAllocationAndDestroy = true;
			return this;
		}

		public TransactionRequest build() {
			return new TransactionRequest(message, actions, feePayer, disableResourceAllocationAndDestroy);
		}
	}
}
