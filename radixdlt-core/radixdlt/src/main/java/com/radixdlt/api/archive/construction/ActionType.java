/*
 * Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
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

package com.radixdlt.api.archive.construction;

import com.radixdlt.api.archive.InvalidParametersException;
import com.radixdlt.api.archive.JsonObjectReader;
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
import com.radixdlt.atom.actions.UpdateAllowDelegationFlag;
import com.radixdlt.atom.actions.UpdateValidatorFee;
import com.radixdlt.atom.actions.UpdateValidatorMetadata;
import com.radixdlt.atom.actions.UpdateValidatorOwner;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.functional.Result;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.radixdlt.application.validators.scrypt.ValidatorUpdateRakeConstraintScrypt.*;
import static com.radixdlt.errors.ApiErrors.UNKNOWN_ACTION;

public enum ActionType {
	MSG("Message") {
		@Override
		TxAction parseAction(JsonObjectReader reader) {
			throw new UnsupportedOperationException();
		}
	},
	TRANSFER("TokenTransfer") {
		@Override
		TxAction parseAction(JsonObjectReader reader) throws InvalidParametersException {
			var from = reader.getAccountAddress("from");
			var to = reader.getAccountAddress("to");
			var amount = reader.getAmount("amount");
			var resourceAddr = reader.getResource("rri");
			return new TransferToken(resourceAddr, from, to, amount);
		}
	},
	STAKE("StakeTokens") {
		@Override
		TxAction parseAction(JsonObjectReader reader) throws InvalidParametersException {
			var from = reader.getAccountAddress("from");
			var validator = reader.getValidatorIdentifier("validator");
			var amount = reader.getAmount("amount");
			return new StakeTokens(from, validator, amount);
		}
	},
	UNSTAKE("UnstakeTokens") {
		@Override
		TxAction parseAction(JsonObjectReader reader) throws InvalidParametersException {
			var from = reader.getAccountAddress("from");
			var validator = reader.getValidatorIdentifier("validator");
			var amount = reader.getAmount("amount");
			return new UnstakeTokens(from, validator, amount);
		}
	},
	BURN("BurnTokens") {
		@Override
		TxAction parseAction(JsonObjectReader reader) throws InvalidParametersException {
			var resourceAddr = reader.getResource("rri");
			var from = reader.getAccountAddress("from");
			var amount = reader.getAmount("amount");
			return new BurnToken(resourceAddr, from, amount);
		}
	},
	MINT("MintTokens") {
		@Override
		TxAction parseAction(JsonObjectReader reader) throws InvalidParametersException {
			var resourceAddr = reader.getResource("rri");
			var to = reader.getAccountAddress("to");
			var amount = reader.getAmount("amount");
			return new MintToken(resourceAddr, to, amount);
		}
	},
	REGISTER_VALIDATOR("RegisterValidator") {
		@Override
		TxAction parseAction(JsonObjectReader reader) throws InvalidParametersException {
			var validator = reader.getValidatorIdentifier("validator");
			return new RegisterValidator(validator);
		}
	},
	UNREGISTER_VALIDATOR("UnregisterValidator") {
		@Override
		TxAction parseAction(JsonObjectReader reader) throws InvalidParametersException {
			var validator = reader.getValidatorIdentifier("validator");
			return new UnregisterValidator(validator);
		}
	},
	UPDATE_VALIDATOR_METADATA("UpdateValidatorMetadata") {
		@Override
		TxAction parseAction(JsonObjectReader reader) throws InvalidParametersException {
			var validator = reader.getValidatorIdentifier("validator");
			var name = reader.getOptString("name").orElse(null);
			var url = reader.getOptString("url").orElse(null);
			return new UpdateValidatorMetadata(validator, name, url);
		}
	},
	UPDATE_VALIDATOR_FEE("UpdateValidatorFee") {
		@Override
		TxAction parseAction(JsonObjectReader reader) throws InvalidParametersException {
			var validator = reader.getValidatorIdentifier("validator");
			var validatorFeeString = reader.getString("validatorFee");
			// TODO: Move parsing to a better place
			int validatorFee;
			try {
				validatorFee = (int) (Double.parseDouble(validatorFeeString) * RAKE_PERCENTAGE_GRANULARITY);
			} catch (NumberFormatException e) {
				throw new InvalidParametersException("/validatorFee", e);
			}

			if (validatorFee < RAKE_MIN || validatorFee > RAKE_MAX) {
				throw new InvalidParametersException("/validatorFee", "Invalid fee amount");
			}
			return new UpdateValidatorFee(validator, validatorFee);
		}
	},
	UPDATE_OWNER("UpdateValidatorOwnerAddress") {
		@Override
		TxAction parseAction(JsonObjectReader reader) throws InvalidParametersException {
			var validator = reader.getValidatorIdentifier("validator");
			var owner = reader.getAccountAddress("owner");
			return new UpdateValidatorOwner(validator, owner);
		}
	},
	UPDATE_DELEGATION("UpdateAllowDelegationFlag") {
		@Override
		TxAction parseAction(JsonObjectReader reader) throws InvalidParametersException {
			var validator = reader.getValidatorIdentifier("validator");
			var allowDelegation = reader.getBoolean("allowDelegation");
			return new UpdateAllowDelegationFlag(validator, allowDelegation);
		}
	},
	CREATE_FIXED("CreateFixedSupplyToken") {
		@Override
		TxAction parseAction(JsonObjectReader reader) throws InvalidParametersException {
			var to = reader.getAccountAddress("to");
			var pubKey = reader.getPubKey("pubKey");
			var symbol = reader.getString("symbol");
			var resourceAddress = REAddr.ofHashedKey(pubKey, symbol);
			var name = reader.getString("name");
			var description = reader.getOptString("description").orElse("");
			var iconUrl = reader.getOptString("iconUrl").orElse("");
			var url = reader.getOptString("url").orElse("");
			var supply = reader.getAmount("supply");
			return new CreateFixedToken(resourceAddress, to, symbol, name, description, iconUrl, url, supply);
		}
	},
	CREATE_MUTABLE("CreateMutableSupplyToken") {
		@Override
		TxAction parseAction(JsonObjectReader reader) throws InvalidParametersException {
			var pubKey = reader.getPubKey("pubKey");
			var symbol = reader.getString("symbol");
			var name = reader.getString("name");
			var description = reader.getOptString("description").orElse("");
			var iconUrl = reader.getOptString("iconUrl").orElse("");
			var url = reader.getOptString("url").orElse("");
			return new CreateMutableToken(pubKey, symbol, name, description, iconUrl, url);
		}
	},
	UNKNOWN("Other") {
		@Override
		TxAction parseAction(JsonObjectReader reader) {
			throw new UnsupportedOperationException();
		}
	};

	private final String text;

	private static final Map<String, ActionType> TO_ACTION_TYPE = Arrays.stream(values())
		.collect(Collectors.toMap(ActionType::toString, Function.identity()));

	ActionType(String text) {
		this.text = text;
	}

	@Override
	public String toString() {
		return text;
	}

	public static ActionType parse(String action) throws IllegalArgumentException {
		var type = TO_ACTION_TYPE.get(action);
		if (type == null) {
			throw new IllegalArgumentException("Unknown action type: " + action);
		}
		return type;
	}

	abstract TxAction parseAction(JsonObjectReader reader) throws InvalidParametersException;

	public static Result<ActionType> fromString(String action) {
		return Optional.ofNullable(TO_ACTION_TYPE.get(action))
			.map(Result::ok)
			.orElseGet(() -> UNKNOWN_ACTION.with(action).result());
	}
}
