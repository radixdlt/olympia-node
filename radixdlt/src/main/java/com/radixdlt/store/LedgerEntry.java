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

package com.radixdlt.store;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.common.AID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;

@SerializerId2("ledger.entry")
public class LedgerEntry {

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("content")
	@DsonOutput(value = {DsonOutput.Output.ALL})
	private byte[] content;

	@JsonProperty("aid")
	@DsonOutput(value = {DsonOutput.Output.ALL})
	private AID aid;

	private LedgerEntry() {
		// For serializer
	}

	public LedgerEntry(byte[] content, AID aid) {
		this.content = Objects.requireNonNull(content, "content is required");
		this.aid = Objects.requireNonNull(aid, "aid is required");
	}

	public byte[] getContent() {
		return this.content;
	}

	public AID getAID() {
		return this.aid;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		LedgerEntry radixLedgerEntry = (LedgerEntry) o;
		return aid.equals(radixLedgerEntry.aid);
	}

	@Override
	public int hashCode() {
		return aid.hashCode();
	}

	@Override
	public String toString() {
		return String.format("LedgerEntry{aid=%s}", aid);
	}
}
