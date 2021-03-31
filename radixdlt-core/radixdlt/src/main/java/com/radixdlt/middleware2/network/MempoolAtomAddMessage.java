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

import java.util.Arrays;
import java.util.Objects;

import org.radix.network.messaging.Message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

@SerializerId2("message.mempool.atomadd")
public final class MempoolAtomAddMessage extends Message {
	@JsonProperty("txn")
	@DsonOutput(Output.ALL)
	private final byte[] txn;

	MempoolAtomAddMessage() {
		// Serializer only
		super(0);
		this.txn = new byte[0];
	}

	public MempoolAtomAddMessage(int magic, Txn txn) {
		super(magic);
		this.txn = txn.getPayload();
	}

	public Txn getTxn() {
		return Txn.create(txn == null ? new byte[0] : txn);
	}

	@Override
	public String toString() {
		return String.format("%s{txn=%s}", getClass().getSimpleName(), getTxn().getId());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		MempoolAtomAddMessage that = (MempoolAtomAddMessage) o;
		return Arrays.equals(txn, that.txn)
				&& Objects.equals(getTimestamp(), that.getTimestamp())
				&& Objects.equals(getMagic(), that.getMagic());
	}

	@Override
	public int hashCode() {
		return Objects.hash(Arrays.hashCode(txn), getTimestamp(), getMagic());
	}
}
