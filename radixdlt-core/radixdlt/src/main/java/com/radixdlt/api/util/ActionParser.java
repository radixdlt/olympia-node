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

package com.radixdlt.api.util;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.inject.Inject;
import com.radixdlt.api.archive.construction.ActionType;
import com.radixdlt.api.data.action.TransactionAction;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Failure;
import com.radixdlt.utils.functional.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.radixdlt.application.validators.scrypt.ValidatorUpdateRakeConstraintScrypt.RAKE_MAX;
import static com.radixdlt.application.validators.scrypt.ValidatorUpdateRakeConstraintScrypt.RAKE_MIN;
import static com.radixdlt.application.validators.scrypt.ValidatorUpdateRakeConstraintScrypt.RAKE_PERCENTAGE_GRANULARITY;
import static com.radixdlt.errors.ApiErrors.INVALID_ACTION_DATA;
import static com.radixdlt.errors.ApiErrors.INVALID_VALUE_OUT_OF_RANGE;
import static com.radixdlt.errors.ApiErrors.MISSING_ACTION_FIELD;
import static com.radixdlt.errors.ApiErrors.UNABLE_TO_PARSE_BOOLEAN;
import static com.radixdlt.errors.ApiErrors.UNABLE_TO_PARSE_FLOAT;
import static com.radixdlt.errors.ApiErrors.UNSUPPORTED_ACTION;
import static com.radixdlt.utils.functional.Result.allOf;
import static com.radixdlt.utils.functional.Result.wrap;

import static java.util.Optional.ofNullable;

public final class ActionParser {
	private final Addressing addressing;

	@Inject
	public ActionParser(Addressing addressing) {
		this.addressing = addressing;
	}

	public Result<List<TransactionAction>> parse(JSONArray actions) {
		var list = new ArrayList<TransactionAction>();

		for (var o : actions) {
			if (!(o instanceof JSONObject)) {
				return INVALID_ACTION_DATA.with(o).result();
			}

			var element = (JSONObject) o;

			var result = param(element, "type")
				.flatMap(ActionType::fromString)
				.flatMap(type -> parseByType(type, element))
				.onSuccess(list::add);

			if (!result.isSuccess()) {
				return result.map(List::of);
			}
		}
		return Result.ok(list);
	}

	private Result<TransactionAction> parseByType(ActionType type, JSONObject element) {
		switch (type) {
			case UNKNOWN:
			case MSG:
			default:
				return UNSUPPORTED_ACTION.with(type).result();

			case TRANSFER:
				return allOf(
					from(element),
					to(element),
					amount(element),
					rri(element)
				).map(TransactionAction::transfer);

			case UNSTAKE:
				return allOf(
					to(element),
					validator(element),
					amount(element)
				).map(TransactionAction::unstake);

			case STAKE:
				return allOf(
					from(element),
					validator(element),
					amount(element)
				).map(TransactionAction::stake);

			case BURN:
				return allOf(
					from(element),
					rri(element),
					amount(element)
				).map(TransactionAction::burn);

			case MINT:
				return allOf(
					to(element),
					rri(element),
					amount(element)
				).map(TransactionAction::mint);

			case REGISTER_VALIDATOR:
				return allOf(
					validator(element)
				).map(TransactionAction::register);

			case UNREGISTER_VALIDATOR:
				return allOf(
					validator(element)
				).map(TransactionAction::unregister);

			case UPDATE_VALIDATOR_METADATA:
				return allOf(
					validator(element),
					optionalName(element),
					optionalUrl(element)
				).map(TransactionAction::updateMetadata);

			case UPDATE_VALIDATOR_FEE:
				return allOf(
					validator(element),
					fee(element)
				).map(TransactionAction::updateValidatorFee);

			case UPDATE_DELEGATION:
				return allOf(
					validator(element),
					allowDelegation(element)
				).map(TransactionAction::updateAllowDelegation);

			case UPDATE_OWNER:
				return allOf(
					validator(element),
					owner(element)
				).map(TransactionAction::updateOwnerAddress);

			case CREATE_FIXED:
				return allOf(
					to(element),
					pubKey(element),
					symbol(element),
					name(element),
					description(element),
					iconUrl(element),
					tokenUrl(element),
					supply(element)
				).map(TransactionAction::createFixed);

			case CREATE_MUTABLE:
				return allOf(
					pubKeyOrSystem(element),
					symbol(element),
					name(element),
					optionalDescription(element),
					optionalIconUrl(element),
					optionalTokenUrl(element)
				).map(TransactionAction::createMutable);
		}
	}

	private static Result<Optional<ECPublicKey>> pubKeyOrSystem(JSONObject element) {
		return param(element, "publicKeyOfSigner")
			.flatMap(k -> k.equals("system") ? Result.ok(Optional.empty()) : ECPublicKey.fromHexString(k).map(Optional::of));
	}

	private Result<ECPublicKey> pubKey(JSONObject element) {
		return param(element, "publicKeyOfSigner")
			.flatMap(ECPublicKey::fromHexString);
	}

	private Result<REAddr> rri(JSONObject element) {
		// TODO: Need to verify symbol matches
		return param(element, "rri")
			.flatMap(p -> addressing.forResources().parseToAddr(p));
	}

	private static final Failure FEE_BOUNDS_FAILURE = INVALID_VALUE_OUT_OF_RANGE.with(
		(double) RAKE_MIN / (double) RAKE_PERCENTAGE_GRANULARITY + "",
		(double) RAKE_MAX / (double) RAKE_PERCENTAGE_GRANULARITY + ""
	);

	private Result<Integer> fee(JSONObject element) {
		return param(element, "validatorFee")
			.flatMap(parameter -> wrap(() -> UNABLE_TO_PARSE_FLOAT.with(parameter), () -> Double.parseDouble(parameter)))
			.map(doublePercentage -> (int) (doublePercentage * RAKE_PERCENTAGE_GRANULARITY))
			.filter(percentage -> percentage >= RAKE_MIN && percentage <= RAKE_MAX, FEE_BOUNDS_FAILURE);
	}

	private Result<Boolean> allowDelegation(JSONObject element) {
		return param(element, "allowDelegation")
			.flatMap(parameter -> Stream.of("true", "false")
				.filter(parameter::equals)
				.findAny()
				.map(Boolean::parseBoolean)
				.map(Result::ok)
				.orElseGet(() -> UNABLE_TO_PARSE_BOOLEAN.with(parameter).result()));
	}

	private Result<REAddr> from(JSONObject element) {
		return address(element, "from");
	}

	private Result<REAddr> owner(JSONObject element) {
		return address(element, "owner");
	}

	private Result<REAddr> to(JSONObject element) {
		return address(element, "to");
	}

	private Result<ECPublicKey> validator(JSONObject element) {
		return param(element, "validator")
			.flatMap(addressing.forValidators()::fromString);
	}

	private static Result<UInt256> amount(JSONObject element) {
		return param(element, "amount")
			.flatMap(UInt256::fromString);
	}

	private static Result<UInt256> supply(JSONObject element) {
		return param(element, "supply")
			.flatMap(UInt256::fromString);
	}

	private static Result<String> name(JSONObject element) {
		return param(element, "name");
	}

	private static Result<Optional<String>> optionalName(JSONObject element) {
		return optionalParam(element, "name");
	}

	private static Result<String> symbol(JSONObject element) {
		return param(element, "symbol");
	}

	private static Result<Optional<String>> optionalUrl(JSONObject element) {
		return optionalParam(element, "url");
	}

	private static Result<String> iconUrl(JSONObject element) {
		return param(element, "iconUrl");
	}

	private static Result<Optional<String>> optionalIconUrl(JSONObject element) {
		return optionalParam(element, "iconUrl");
	}

	private static Result<String> tokenUrl(JSONObject element) {
		return param(element, "tokenUrl");
	}

	private static Result<Optional<String>> optionalTokenUrl(JSONObject element) {
		return optionalParam(element, "tokenUrl");
	}

	private static Result<String> description(JSONObject element) {
		return param(element, "description");
	}

	private static Result<Optional<String>> optionalDescription(JSONObject element) {
		return optionalParam(element, "description");
	}

	private static Result<Optional<String>> optionalParam(JSONObject element, String name) {
		return Result.ok(
			ofNullable(element.opt(name))
				.filter(String.class::isInstance)
				.map(String.class::cast)
		);
	}

	private Result<REAddr> address(JSONObject element, String name) {
		return param(element, name)
			.flatMap(addressing.forAccounts()::parseFunctional);
	}

	private static Result<String> param(JSONObject params, String name) {
		return ofNullable(params.opt(name))
			.map(Object::toString)
			.map(Result::ok)
			.orElseGet(() -> MISSING_ACTION_FIELD.with(name).result());
	}
}
