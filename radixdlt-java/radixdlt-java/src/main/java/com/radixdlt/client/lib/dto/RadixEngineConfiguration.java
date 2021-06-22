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

import java.util.List;
import java.util.Objects;

public class RadixEngineConfiguration {
	private final List<ForkConfig> forks;
	private final long maxValidators;
	private final long minValidators;
	private final long maxTxnsPerProposal;

	private RadixEngineConfiguration(List<ForkConfig> forks, long maxValidators, long minValidators, long maxTxnsPerProposal) {
		this.forks = forks;
		this.maxValidators = maxValidators;
		this.minValidators = minValidators;
		this.maxTxnsPerProposal = maxTxnsPerProposal;
	}

	@JsonCreator
	public static RadixEngineConfiguration create(
		@JsonProperty(value = "forks", required = true) List<ForkConfig> forks,
		@JsonProperty(value = "maxValidators", required = true) long maxValidators,
		@JsonProperty(value = "minValidators", required = true) long minValidators,
		@JsonProperty(value = "maxTxnsPerProposal", required = true) long maxTxnsPerProposal
	) {
		return new RadixEngineConfiguration(forks, maxValidators, minValidators, maxTxnsPerProposal);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof RadixEngineConfiguration)) {
			return false;
		}

		var that = (RadixEngineConfiguration) o;
		return maxValidators == that.maxValidators
			&& minValidators == that.minValidators
			&& maxTxnsPerProposal == that.maxTxnsPerProposal
			&& forks.equals(that.forks);
	}

	@Override
	public int hashCode() {
		return Objects.hash(forks, maxValidators, minValidators, maxTxnsPerProposal);
	}

	@Override
	public String toString() {
		return "{forks:" + forks
			+ ", maxValidators:" + maxValidators
			+ ", minValidators:" + minValidators
			+ ", maxTxnsPerProposal:" + maxTxnsPerProposal + '}';
	}

	public List<ForkConfig> getForks() {
		return forks;
	}

	public long getMaxValidators() {
		return maxValidators;
	}

	public long getMinValidators() {
		return minValidators;
	}

	public long getMaxTxnsPerProposal() {
		return maxTxnsPerProposal;
	}
}
