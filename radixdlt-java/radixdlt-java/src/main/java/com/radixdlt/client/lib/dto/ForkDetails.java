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

public final class ForkDetails {
	private final String name;
	private final String version;
	private final long epoch;
	private final long maxRounds;
	private final long maxSigsPerRound;
	private final long maxValidators;

	private ForkDetails(String name, String version, long epoch, long maxRounds, long maxSigsPerRound, long maxValidators) {
		this.name = name;
		this.version = version;
		this.epoch = epoch;
		this.maxRounds = maxRounds;
		this.maxSigsPerRound = maxSigsPerRound;
		this.maxValidators = maxValidators;
	}

	@JsonCreator
	public static ForkDetails create(
		@JsonProperty(value = "name", required = true) String name,
		@JsonProperty(value = "version", required = true) String version,
		@JsonProperty(value = "epoch", required = true) long epoch,
		@JsonProperty(value = "maxRounds", required = true) long maxRounds,
		@JsonProperty(value = "maxSigsPerRound", required = true) long maxSigsPerRound,
		@JsonProperty(value = "maxValidators", required = true) long maxValidators
	) {
		return new ForkDetails(name, version, epoch, maxRounds, maxSigsPerRound, maxValidators);
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
			&& maxValidators == that.maxValidators
			&& name.equals(that.name)
			&& version.equals(that.version);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, version, epoch, maxRounds, maxSigsPerRound, maxValidators);
	}

	@Override
	public String toString() {
		return "{name:'" + name + '\''
			+ ", version:'" + version + '\''
			+ ", epoch:" + epoch
			+ ", maxRounds:" + maxRounds
			+ ", maxSigsPerRound:" + maxSigsPerRound
			+ ", maxValidators:" + maxValidators + '}';
	}

	public String getName() {
		return name;
	}

	public String getVersion() {
		return version;
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

	public long getMaxValidators() {
		return maxValidators;
	}
}
