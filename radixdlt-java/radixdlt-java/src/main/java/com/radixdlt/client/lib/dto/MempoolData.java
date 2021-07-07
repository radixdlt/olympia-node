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

public final class MempoolData {
	private final long maxcount;
	private final long relayerSentCount;
	private final long count;
	private final long addSuccess;
	private final long proposedTransaction;
	private final MempoolDataErrors errors;

	private MempoolData(
		long maxcount,
		long relayerSentCount,
		long count,
		long addSuccess,
		long proposedTransaction,
		MempoolDataErrors errors
	) {
		this.maxcount = maxcount;
		this.relayerSentCount = relayerSentCount;
		this.count = count;
		this.addSuccess = addSuccess;
		this.proposedTransaction = proposedTransaction;
		this.errors = errors;
	}

	@JsonCreator
	public static MempoolData create(
		@JsonProperty(value = "maxcount", required = true) long maxcount,
		@JsonProperty(value = "relayerSentCount", required = true) long relayerSentCount,
		@JsonProperty(value = "count", required = true) long count,
		@JsonProperty(value = "addSuccess", required = true) long addSuccess,
		@JsonProperty(value = "proposedTransaction", required = true) long proposedTransaction,
		@JsonProperty(value = "errors", required = true) MempoolDataErrors errors
	) {
		return new MempoolData(maxcount, relayerSentCount, count, addSuccess, proposedTransaction, errors);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof MempoolData)) {
			return false;
		}
		MempoolData that = (MempoolData) o;
		return maxcount == that.maxcount
			&& relayerSentCount == that.relayerSentCount
			&& count == that.count
			&& addSuccess == that.addSuccess
			&& proposedTransaction == that.proposedTransaction
			&& errors.equals(that.errors);
	}

	@Override
	public int hashCode() {
		return Objects.hash(maxcount, relayerSentCount, count, addSuccess, proposedTransaction, errors);
	}

	@Override
	public String toString() {
		return "{" + "maxcount:" + maxcount
			+ ", relayerSentCount:" + relayerSentCount
			+ ", count:" + count
			+ ", addSuccess:" + addSuccess
			+ ", proposedTransaction:" + proposedTransaction
			+ ", errors:" + errors + '}';
	}

	public long getMaxcount() {
		return maxcount;
	}

	public long getRelayerSentCount() {
		return relayerSentCount;
	}

	public long getCount() {
		return count;
	}

	public long getAddSuccess() {
		return addSuccess;
	}

	public long getProposedTransaction() {
		return proposedTransaction;
	}

	public MempoolDataErrors getErrors() {
		return errors;
	}
}
