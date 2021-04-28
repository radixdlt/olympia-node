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

package com.radixdlt.client.handler;

import org.json.JSONArray;
import org.json.JSONObject;

import com.radixdlt.client.AccountAddress;
import com.radixdlt.client.ValidatorAddress;
import com.radixdlt.client.api.ActionType;
import com.radixdlt.client.api.TransactionAction;
import com.radixdlt.client.store.ClientApiStore;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.radixdlt.utils.functional.Failure.failure;
import static com.radixdlt.utils.functional.Result.allOf;

import static java.util.Optional.ofNullable;

public final class ActionParser {
	private static final Result<Optional<REAddr>> EMPTY_RESULT = Result.ok(Optional.empty());

	private ActionParser() { }

	public static Result<List<TransactionAction>> parse(JSONArray actions, ClientApiStore clientApiStore) {
		var list = new ArrayList<TransactionAction>();

		for (var o : actions) {
			if (!(o instanceof JSONObject)) {
				return failure("Unable to recognize action description {0}", o).result();
			}

			var element = (JSONObject) o;

			var result = safeString(element, "type")
				.flatMap(ActionType::fromString)
				.flatMap(type -> parseByType(type, element, clientApiStore))
				.onSuccess(list::add);

			if (!result.isSuccess()) {
				return result.map(List::of);
			}
		}
		return Result.ok(list);
	}

	private static Result<TransactionAction> parseByType(ActionType type, JSONObject element, ClientApiStore clientApiStore) {
		var typeResult = Result.ok(type);

		switch (type) {
			case TRANSFER:
				return allOf(
					typeResult,
					from(element),
					to(element),
					amount(element),
					rri(element, clientApiStore)
				).map(TransactionAction::create);

			case UNSTAKE:
			case STAKE:
				return allOf(
					typeResult,
					from(element),
					validator(element),
					amount(element),
					EMPTY_RESULT
				).map(TransactionAction::create);
		}

		return failure("Action type {0} is not supported (yet)", type).result();
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
		return safeString(element, "amount")
			.flatMap(UInt256::fromString);
	}

	private static Result<Optional<REAddr>> rri(JSONObject element, ClientApiStore clientApiStore) {
		return safeString(element, "tokenIdentifier")
			.flatMap(clientApiStore::parseRri)
			.map(Optional::of);
	}

	private static Result<ECPublicKey> validator(JSONObject element, String name) {
		return safeString(element, name)
			.flatMap(ValidatorAddress::fromString);
	}

	private static Result<REAddr> address(JSONObject element, String name) {
		return safeString(element, name)
			.flatMap(AccountAddress::parseFunctional);
	}

	private static Result<String> safeString(JSONObject params, String name) {
		return ofNullable(params.opt(name))
			.map(Object::toString).map(Result::ok)
			.orElseGet(() -> failure("Field {0} is missing or contains invalid value", name).result());
	}
}
