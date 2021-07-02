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

package com.radixdlt.api.service;

import com.radixdlt.networks.Addressing;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.statecomputer.forks.ForkManager;
import org.json.JSONArray;
import org.json.JSONObject;

import com.google.inject.Inject;
import com.radixdlt.api.data.ActionType;
import com.radixdlt.api.data.action.TransactionAction;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.radixdlt.api.data.ApiErrors.INVALID_ACTION_DATA;
import static com.radixdlt.api.data.ApiErrors.MISSING_FIELD;
import static com.radixdlt.api.data.ApiErrors.UNSUPPORTED_ACTION;
import static com.radixdlt.identifiers.CommonErrors.UNABLE_TO_DECODE;
import static com.radixdlt.utils.functional.Result.allOf;
import static com.radixdlt.utils.functional.Result.wrap;

import static java.util.Optional.ofNullable;

public final class ActionParserService {
	private final Addressing addressing;
	private final ForkManager forkManager;

	@Inject
	public ActionParserService(Addressing addressing, ForkManager forkManager) {
		this.addressing = addressing;
		this.forkManager = forkManager;
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
					from(element),
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

			case UPDATE_VALIDATOR:
				return allOf(
					validator(element),
					optionalName(element),
					optionalUrl(element)
				).map((validatorKey, name, url) -> {
					final var maybeForkVoteHash =
						forkManager.getCandidateFork().map(f -> ForkConfig.voteHash(validatorKey, f));
					return TransactionAction.update(validatorKey, name, url, maybeForkVoteHash);
				});

			case UPDATE_RAKE:
				return allOf(
					validator(element),
					percentage(element)
				).map(TransactionAction::updateRake);

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
					from(element),
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
					pubKey(element),
					symbol(element),
					name(element),
					optionalDescription(element),
					optionalIconUrl(element),
					optionalTokenUrl(element)
				).map(TransactionAction::createMutable);
		}
	}

	private Result<ECPublicKey> pubKey(JSONObject element) {
		return param(element, "publicKeyOfSigner")
			.flatMap(ECPublicKey::fromHexString);
	}

	private Result<REAddr> rri(JSONObject element) {
		// TODO: Need to verify symbol matches
		return param(element, "rri")
			.flatMap(p -> addressing.forResources().parseFunctional(p).map(t -> t.map((__, addr) -> addr)));
	}

	private Result<Integer> percentage(JSONObject element) {
		return param(element, "validatorFee")
			.flatMap(parameter -> wrap(UNABLE_TO_DECODE, () -> Integer.parseInt(parameter)));
	}

	private Result<Boolean> allowDelegation(JSONObject element) {
		return param(element, "allowDelegation")
			.flatMap(parameter ->
						 Stream.of("true", "false")
							 .filter(parameter::equals)
							 .findAny()
							 .map(Boolean::parseBoolean)
							 .map(Result::ok)
							 .orElseGet(() -> UNABLE_TO_DECODE.with(parameter).result())
			);
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
			.orElseGet(() -> MISSING_FIELD.with(name).result());
	}
}
