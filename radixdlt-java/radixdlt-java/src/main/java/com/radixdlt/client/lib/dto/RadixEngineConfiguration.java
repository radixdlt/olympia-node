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
	private final List<ForkConfig> knownForks;
	private final ForkConfig currentFork;
	private final long maxValidators;
	private final long minValidators;
	private final long maxTxnsPerProposal;

	private RadixEngineConfiguration(
		List<ForkConfig> knownForks,
		ForkConfig currentFork,
		long maxValidators,
		long minValidators,
		long maxTxnsPerProposal
	) {
		this.knownForks = knownForks;
		this.currentFork = currentFork;
		this.maxValidators = maxValidators;
		this.minValidators = minValidators;
		this.maxTxnsPerProposal = maxTxnsPerProposal;
	}

	@JsonCreator
	public static RadixEngineConfiguration create(
		@JsonProperty(value = "known_forks", required = true) List<ForkConfig> knownForks,
		@JsonProperty(value = "current_fork", required = true) ForkConfig currentFork,
		@JsonProperty(value = "maxValidators", required = true) long maxValidators,
		@JsonProperty(value = "minValidators", required = true) long minValidators,
		@JsonProperty(value = "maxTxnsPerProposal", required = true) long maxTxnsPerProposal
	) {
		return new RadixEngineConfiguration(knownForks, currentFork, maxValidators, minValidators, maxTxnsPerProposal);
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
			&& Objects.equals(knownForks, that.knownForks)
			&& Objects.equals(currentFork, that.currentFork);
	}

	@Override
	public int hashCode() {
		return Objects.hash(knownForks, currentFork, maxValidators, minValidators, maxTxnsPerProposal);
	}

	@Override
	public String toString() {
		return "{knownForks:" + knownForks
			+ ", currentFork:" + currentFork
			+ ", maxValidators:" + maxValidators
			+ ", minValidators:" + minValidators
			+ ", maxTxnsPerProposal:" + maxTxnsPerProposal + '}';
	}

	public List<ForkConfig> getKnownForks() {
		return knownForks;
	}

	public ForkConfig getCurrentFork() {
		return currentFork;
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
