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

public class ConsensusDataSync {
	private final long requestTimeouts;
	private final long requestsSent;

	private ConsensusDataSync(long requestTimeouts, long requestsSent) {
		this.requestTimeouts = requestTimeouts;
		this.requestsSent = requestsSent;
	}

	@JsonCreator
	public static ConsensusDataSync create(
		@JsonProperty("request_timeouts") long requestTimeouts,
		@JsonProperty("requests_sent") long requestsSent
	) {
		return new ConsensusDataSync(requestTimeouts, requestsSent);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof ConsensusDataSync)) {
			return false;
		}

		var that = (ConsensusDataSync) o;
		return requestTimeouts == that.requestTimeouts && requestsSent == that.requestsSent;
	}

	@Override
	public int hashCode() {
		return Objects.hash(requestTimeouts, requestsSent);
	}

	@Override
	public String toString() {
		return "{requestTimeouts:" + requestTimeouts + ", requestsSent:" + requestsSent + '}';
	}

	public long getRequestTimeouts() {
		return requestTimeouts;
	}

	public long getRequestsSent() {
		return requestsSent;
	}
}
