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

public final class MempoolConfiguration {
	private final long throttleMs;
	private final long maxSize;

	private MempoolConfiguration(long throttleMs, long maxSize) {
		this.throttleMs = throttleMs;
		this.maxSize = maxSize;
	}

	@JsonCreator
	public static MempoolConfiguration create(
		@JsonProperty(value = "throttleMs", required = true) long throttleMs,
		@JsonProperty(value = "maxSize", required = true) long maxSize
	) {
		return new MempoolConfiguration(throttleMs, maxSize);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof MempoolConfiguration)) {
			return false;
		}

		var that = (MempoolConfiguration) o;
		return throttleMs == that.throttleMs && maxSize == that.maxSize;
	}

	@Override
	public int hashCode() {
		return Objects.hash(throttleMs, maxSize);
	}

	@Override
	public String toString() {
		return "{throttleMs:" + throttleMs + ", maxSize:" + maxSize + '}';
	}

	public long getThrottleMs() {
		return throttleMs;
	}

	public long getMaxSize() {
		return maxSize;
	}
}
