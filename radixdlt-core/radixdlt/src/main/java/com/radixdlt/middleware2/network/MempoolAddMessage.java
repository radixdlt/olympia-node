/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.middleware2.network;

import com.radixdlt.atom.Txn;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.radix.network.messaging.Message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

@SerializerId2("message.mempool.add")
public final class MempoolAddMessage extends Message {
	@JsonProperty("txns")
	@DsonOutput(Output.ALL)
	private final List<byte[]> txns;

	MempoolAddMessage() {
		// Serializer only
		this.txns = null;
	}

	public MempoolAddMessage(List<Txn> txns) {
		this.txns = txns.stream().map(Txn::getPayload).collect(Collectors.toList());
	}

	public List<Txn> getTxns() {
		return txns == null ? List.of() : txns.stream().map(Txn::create).collect(Collectors.toList());
	}

	@Override
	public String toString() {
		return String.format("%s{txns=%s}", getClass().getSimpleName(), getTxns());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		MempoolAddMessage that = (MempoolAddMessage) o;
		return Objects.equals(getTxns(), that.getTxns())
				&& Objects.equals(getTimestamp(), that.getTimestamp());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getTxns(), getTimestamp());
	}
}
