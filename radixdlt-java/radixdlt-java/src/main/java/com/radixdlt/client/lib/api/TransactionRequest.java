/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.client.lib.api;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import com.radixdlt.client.lib.api.action.UpdateValidatorMetadataAction;
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

		public TransactionRequestBuilder unstake(AccountAddress to, ValidatorAddress validator, UInt256 amount) {
			actions.add(new UnstakeAction(to, validator, amount));
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

		public TransactionRequestBuilder updateValidatorMetadata(ValidatorAddress validator, Optional<String> name, Optional<String> url) {
			actions.add(new UpdateValidatorMetadataAction(validator, name.orElse(null), url.orElse(null)));
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
			actions.add(new CreateFixedTokenAction(
				to, publicKeyOfSigner, symbol, name,
				description, iconUrl, tokenUrl, supply
			));
			return this;
		}

		public TransactionRequestBuilder createMutable(
			ECPublicKey publicKeyOfSigner, String symbol, String name,
			Optional<String> description, Optional<String> iconUrl, Optional<String> tokenUrl
		) {
			actions.add(new CreateMutableTokenAction(
				publicKeyOfSigner, symbol, name,
				description.orElse(null),
				iconUrl.orElse(null),
				tokenUrl.orElse(null)
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
