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

import com.radixdlt.client.api.ActionType;
import com.radixdlt.client.api.TransactionAction;
import com.radixdlt.identifiers.Rri;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.radix.api.jsonrpc.JsonRpcUtil.safeString;

import static com.radixdlt.utils.functional.Result.allOf;

public final class ActionParser {
	private static final Result<Optional<Rri>> EMPTY_RESULT = Result.ok(Optional.empty());

	private ActionParser() { }

	public static Result<List<TransactionAction>> parse(JSONArray actions) {
		var list = new ArrayList<TransactionAction>();

		for (var o : actions) {
			if (!(o instanceof JSONObject)) {
				return Result.fail("Unable to recognize action description {0}", o);
			}

			var element = (JSONObject) o;

			var result = Result.fromOptional(
				safeString(element, "type")
					.flatMap(ActionType::fromString),
				"Action type is missing or can not be parsed in {0}", element
			)
				.flatMap(type -> parseByType(type, element))
				.onSuccess(list::add);

			if (!result.isSuccess()) {
				return result.map(List::of);
			}
		}
		return Result.ok(list);
	}

	private static Result<TransactionAction> parseByType(ActionType type, JSONObject element) {
		var typeResult = Result.ok(type);

		switch (type) {
			case TRANSFER:
				return allOf(
					typeResult,
					from(element),
					to(element),
					amount(element),
					rri(element)
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

		return Result.fail("Action type {0} is not supported (yet)", type);
	}

	private static Result<RadixAddress> from(JSONObject element) {
		return address(element, "from");
	}

	private static Result<RadixAddress> to(JSONObject element) {
		return address(element, "to");
	}

	private static Result<RadixAddress> validator(JSONObject element) {
		return address(element, "validator");
	}

	private static Result<UInt256> amount(JSONObject element) {
		return safeString(element, "amount")
			.flatMap(UInt256::fromString)
			.map(Result::ok)
			.orElseGet(() -> fail(element, "amount"));
	}

	private static Result<Optional<Rri>> rri(JSONObject element) {
		return Result.fromOptional(safeString(element, "rri"), "Field rri is missing in {0}", element)
			.flatMap(Rri::fromString)
			.map(Optional::of);
	}

	private static Result<RadixAddress> address(JSONObject element, String name) {
		return safeString(element, name)
			.flatMap(RadixAddress::fromString)
			.map(Result::ok)
			.orElseGet(() -> fail(element, name));
	}

	private static <T> Result<T> fail(JSONObject element, String field) {
		return Result.fail("Field {1} is missing or contains invalid value in {0}", element, field);
	}
}
