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

import java.util.Objects;

public class ApiDataElapsed {
	private final ApiDbElapsed apiDb;

	private ApiDataElapsed(ApiDbElapsed apiDb) {
		this.apiDb = apiDb;
	}

	@JsonCreator
	public static ApiDataElapsed create(@JsonProperty(value = "apidb", required = true) ApiDbElapsed apiDb) {
		return new ApiDataElapsed(apiDb);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof ApiDataElapsed)) {
			return false;
		}

		var that = (ApiDataElapsed) o;
		return apiDb.equals(that.apiDb);
	}

	@Override
	public int hashCode() {
		return Objects.hash(apiDb);
	}

	@Override
	public String toString() {
		return "{apiDb:" + apiDb + '}';
	}

	public ApiDbElapsed getApiDb() {
		return apiDb;
	}
}
