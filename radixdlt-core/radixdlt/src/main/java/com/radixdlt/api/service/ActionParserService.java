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

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.inject.Inject;
import com.radixdlt.api.data.ActionType;
import com.radixdlt.api.data.TransactionAction;
import com.radixdlt.api.store.ClientApiStore;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.AccountAddress;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.identifiers.ValidatorAddress;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.radixdlt.api.data.ApiErrors.INVALID_ACTION_DATA;
import static com.radixdlt.api.data.ApiErrors.MISSING_FIELD;
import static com.radixdlt.api.data.ApiErrors.UNSUPPORTED_ACTION;
import static com.radixdlt.utils.functional.Result.allOf;

import static java.util.Optional.ofNullable;

public final class ActionParserService {
	private final ClientApiStore clientApiStore;

	@Inject
	public ActionParserService(ClientApiStore clientApiStore) {
		this.clientApiStore = clientApiStore;
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
			case MSG:
				break;
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
					validator(element),
					optionalName(element),
					optionalUrl(element)
				).map(TransactionAction::register);

			case UNREGISTER_VALIDATOR:
				return allOf(
					validator(element),
					optionalName(element),
					optionalUrl(element)
				).map(TransactionAction::unregister);

			case CREATE_FIXED:
				return allOf(
					from(element),
					rri(element),
					symbol(element),
					name(element),
					description(element),
					iconUrl(element),
					tokenUrl(element),
					supply(element)
				).map(TransactionAction::createFixed);

			case CREATE_MUTABLE:
				return allOf(
					symbol(element),
					name(element),
					optionalDescription(element),
					optionalIconUrl(element),
					optionalTokenUrl(element)
				).map(TransactionAction::createMutable);
		}

		return UNSUPPORTED_ACTION.with(type).result();
	}

	private Result<REAddr> rri(JSONObject element) {
		return param(element, "rri")
			.flatMap(clientApiStore::parseRri);
	}

	private static Result<REAddr> from(JSONObject element) {
		return address(element, "from");
	}

	private static Result<REAddr> to(JSONObject element) {
		return address(element, "to");
	}

	private static Result<ECPublicKey> validator(JSONObject element) {
		return validator(element, "validator");
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

	private static Result<ECPublicKey> validator(JSONObject element, String name) {
		return param(element, name)
			.flatMap(ValidatorAddress::fromString);
	}

	private static Result<REAddr> address(JSONObject element, String name) {
		return param(element, name)
			.flatMap(AccountAddress::parseFunctional);
	}

	private static Result<String> param(JSONObject params, String name) {
		return ofNullable(params.opt(name))
			.map(Object::toString)
			.map(Result::ok)
			.orElseGet(() -> MISSING_FIELD.with(name).result());
	}
}
