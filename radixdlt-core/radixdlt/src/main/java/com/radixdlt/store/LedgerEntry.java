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
import com.radixdlt.identifiers.AID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;

@SerializerId2("ledger.entry")
public final class LedgerEntry {

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("content")
	@DsonOutput(value = {DsonOutput.Output.ALL})
	private final byte[] content;

	@JsonProperty("aid")
	@DsonOutput(value = {DsonOutput.Output.ALL})
	private final AID aid;

	@JsonProperty("stateVersion")
	@DsonOutput(value = {DsonOutput.Output.ALL})
	private final long stateVersion;

	@JsonProperty("proofVersion")
	@DsonOutput(value = {DsonOutput.Output.ALL})
	private final long proofVersion;


	LedgerEntry() {
		// Serializer only
		this.aid = null;
		this.content = null;
		this.stateVersion = 0;
		this.proofVersion = 0;
	}

	public LedgerEntry(byte[] content, long stateVersion, long proofVersion, AID aid) {
		if (stateVersion < 0) {
			throw new IllegalArgumentException("stateVersion must be >= 0");
		}
		this.content = Objects.requireNonNull(content, "content is required");
		this.stateVersion = stateVersion;
		this.proofVersion = proofVersion;
		this.aid = Objects.requireNonNull(aid, "aid is required");
	}

	/**
	 * Returns the underlying bytes directly (no copy), due to performance reasons.
	 * Do NOT edit the bytes, since it is not a copy.
	 * @return Content of this ledger entry as a byte array, directly accessing the content. Do NOT
	 * modify this. No copy is made for performance reasons.
	 */
	public byte[] getContent() {
		return this.content;
	}

	public long getStateVersion() {
		return stateVersion;
	}

	public long getProofVersion() {
		return proofVersion;
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
		return Objects.equals(aid, radixLedgerEntry.aid)
			&& proofVersion == radixLedgerEntry.proofVersion
			&& stateVersion == radixLedgerEntry.stateVersion;
	}

	@Override
	public int hashCode() {
		return Objects.hash(aid, proofVersion, stateVersion);
	}

	@Override
	public String toString() {
		return String.format("LedgerEntry{aid=%s}", aid);
	}
}
