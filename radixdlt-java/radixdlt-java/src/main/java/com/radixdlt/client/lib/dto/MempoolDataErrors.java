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

public class MempoolDataErrors {
	private final long other;
	private final long hook;
	private final long conflict;

	private MempoolDataErrors(long other, long hook, long conflict) {
		this.other = other;
		this.hook = hook;
		this.conflict = conflict;
	}

	@JsonCreator
	public static MempoolDataErrors create(
		@JsonProperty(value = "other", required = true) long other,
		@JsonProperty(value = "hook", required = true) long hook,
		@JsonProperty(value = "conflict", required = true) long conflict
	) {
		return new MempoolDataErrors(other, hook, conflict);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof MempoolDataErrors)) {
			return false;
		}

		var that = (MempoolDataErrors) o;
		return other == that.other && hook == that.hook && conflict == that.conflict;
	}

	@Override
	public int hashCode() {
		return Objects.hash(other, hook, conflict);
	}

	@Override
	public String toString() {
		return "{other:" + other + ", hook:" + hook + ", conflict:" + conflict + '}';
	}

	public long getOther() {
		return other;
	}

	public long getHook() {
		return hook;
	}

	public long getConflict() {
		return conflict;
	}
}
