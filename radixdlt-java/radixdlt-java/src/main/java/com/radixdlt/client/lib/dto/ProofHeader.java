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

public final class ProofHeader {
	private final long view;
	private final long epoch;
	private final long version;
	private final long timestamp;
	private final String accumulator;
	private final List<ValidatorEntry> nextValidators;

	private ProofHeader(
		long view, long epoch, long version, long timestamp, String accumulator, List<ValidatorEntry> nextValidators
	) {
		this.view = view;
		this.epoch = epoch;
		this.version = version;
		this.timestamp = timestamp;
		this.accumulator = accumulator;
		this.nextValidators = nextValidators;
	}

	@JsonCreator
	public static ProofHeader create(
		@JsonProperty(value = "view", required = true) long view,
		@JsonProperty(value = "epoch", required = true) long epoch,
		@JsonProperty(value = "version", required = true) long version,
		@JsonProperty(value = "timestamp", required = true) long timestamp,
		@JsonProperty(value = "accumulator", required = true) String accumulator,
		@JsonProperty("nextValidators") List<ValidatorEntry> nextValidators
	) {
		return new ProofHeader(
			view, epoch, version, timestamp, accumulator,
			nextValidators == null ? List.of() : nextValidators
		);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof ProofHeader)) {
			return false;
		}

		var that = (ProofHeader) o;
		return view == that.view
			&& epoch == that.epoch
			&& version == that.version
			&& timestamp == that.timestamp
			&& accumulator.equals(that.accumulator)
			&& nextValidators.equals(that.nextValidators);
	}

	@Override
	public int hashCode() {
		return Objects.hash(view, epoch, version, timestamp, accumulator, nextValidators);
	}

	@Override
	public String toString() {
		return "{view:" + view
			+ ", epoch:" + epoch
			+ ", version:" + version
			+ ", timestamp:" + timestamp
			+ ", accumulator:" + accumulator
			+ ", nextValidators" + nextValidators + '}';
	}

	public long getView() {
		return view;
	}

	public long getEpoch() {
		return epoch;
	}

	public long getVersion() {
		return version;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public String getAccumulator() {
		return accumulator;
	}

	public List<ValidatorEntry> getNextValidators() {
		return nextValidators;
	}
}
