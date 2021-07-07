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

package com.radixdlt.client.lib.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.lib.api.NavigationCursor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public final class ValidatorsResponse {
	private final NavigationCursor cursor;
	private final List<ValidatorDTO> validators;

	private ValidatorsResponse(NavigationCursor cursor, List<ValidatorDTO> validators) {
		this.cursor = cursor;
		this.validators = validators;
	}

	@JsonCreator
	public static ValidatorsResponse create(
		@JsonProperty("cursor") NavigationCursor cursor,
		@JsonProperty(value = "validators", required = true) List<ValidatorDTO> validators
	) {
		requireNonNull(validators);

		return new ValidatorsResponse(cursor, validators);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof ValidatorsResponse)) {
			return false;
		}

		var that = (ValidatorsResponse) o;
		return cursor.equals(that.cursor) && validators.equals(that.validators);
	}

	@Override
	public int hashCode() {
		return Objects.hash(cursor, validators);
	}

	@Override
	public String toString() {
		return "ValidatorsResponseDTO(cursor=" + cursor + ", validators=" + validators + ')';
	}

	public Optional<NavigationCursor> getCursor() {
		return Optional.ofNullable(cursor);
	}

	public List<ValidatorDTO> getValidators() {
		return validators;
	}
}
