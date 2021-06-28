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

public class ForkConfig {
	private final String name;
	private final String hash;
	private final long minEpoch;
	private final long maxRounds;

	private ForkConfig(String name, String hash, long minEpoch, long maxRounds) {
		this.name = name;
		this.hash = hash;
		this.minEpoch = minEpoch;
		this.maxRounds = maxRounds;
	}

	@JsonCreator
	public static ForkConfig create(
		@JsonProperty(value = "name", required = true) String name,
		@JsonProperty(value = "hash", required = true) String hash,
		@JsonProperty(value = "minEpoch", required = true) long minEpoch,
		@JsonProperty(value = "maxRounds", required = true) long maxRounds
	) {
		return new ForkConfig(name, hash, minEpoch, maxRounds);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof ForkConfig)) {
			return false;
		}

		var that = (ForkConfig) o;
		return minEpoch == that.minEpoch
			&& maxRounds == that.maxRounds
			&& Objects.equals(name, that.name)
			&& Objects.equals(hash, that.hash);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, hash, minEpoch, maxRounds);
	}

	@Override
	public String toString() {
		return "{name:'" + name + '\'' + ", minEpoch:" + minEpoch + ", maxRounds:" + maxRounds + '}';
	}

	public String getName() {
		return name;
	}

	public long getMinEpoch() {
		return minEpoch;
	}

	public long getMaxRounds() {
		return maxRounds;
	}
}
