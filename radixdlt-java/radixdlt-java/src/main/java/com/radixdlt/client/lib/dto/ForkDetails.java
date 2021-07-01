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

public class ForkDetails {
	private final String name;
	private final long epoch;
	private final long maxRounds;
	private final long maxSigsPerRound;

	private ForkDetails(String name, long epoch, long maxRounds, long maxSigsPerRound) {
		this.name = name;
		this.epoch = epoch;
		this.maxRounds = maxRounds;
		this.maxSigsPerRound = maxSigsPerRound;
	}

	@JsonCreator
	public static ForkDetails create(
		@JsonProperty(value = "name", required = true) String name,
		@JsonProperty(value = "epoch", required = true) long epoch,
		@JsonProperty(value = "maxRounds", required = true) long maxRounds,
		@JsonProperty(value = "maxSigsPerRound", required = true) long maxSigsPerRound
	) {
		return new ForkDetails(name, epoch, maxRounds, maxSigsPerRound);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof ForkDetails)) {
			return false;
		}

		var that = (ForkDetails) o;
		return epoch == that.epoch
			&& maxRounds == that.maxRounds
			&& maxSigsPerRound == that.maxSigsPerRound
			&& name.equals(that.name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, epoch, maxRounds, maxSigsPerRound);
	}

	@Override
	public String toString() {
		return "{name:'" + name + '\'' + ", epoch:" + epoch
			+ ", maxRounds:" + maxRounds + ", maxSigsPerRound:" + maxSigsPerRound + '}';
	}

	public String getName() {
		return name;
	}

	public long getEpoch() {
		return epoch;
	}

	public long getMaxRounds() {
		return maxRounds;
	}

	public long getMaxSigsPerRound() {
		return maxSigsPerRound;
	}
}
