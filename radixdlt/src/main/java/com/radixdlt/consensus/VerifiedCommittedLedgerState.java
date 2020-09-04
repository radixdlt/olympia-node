/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.Hash;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;

@Immutable
@SerializerId2("ledger.verified_committed_ledger_state")
public final class VerifiedCommittedLedgerState implements Comparable<VerifiedCommittedLedgerState> {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("opaque0")
	@DsonOutput(Output.ALL)
	private final BFTHeader opaque0;

	@JsonProperty("opaque1")
	@DsonOutput(Output.ALL)
	private final BFTHeader opaque1;

	@JsonProperty("header")
	@DsonOutput(Output.ALL)
	private final BFTHeader header;

	@JsonProperty("signatures")
	@DsonOutput(Output.ALL)
	private final TimestampedECDSASignatures signatures;

	@JsonCreator
	public VerifiedCommittedLedgerState(
		@JsonProperty("opaque0") BFTHeader opaque0,
		@JsonProperty("opaque1") BFTHeader opaque1,
		@JsonProperty("header") BFTHeader header,
		@JsonProperty("signatures") TimestampedECDSASignatures signatures
	) {
		this.opaque0 = Objects.requireNonNull(opaque0);
		this.opaque1 = Objects.requireNonNull(opaque1);
		this.header = Objects.requireNonNull(header);
		this.signatures = Objects.requireNonNull(signatures);
	}

	public static VerifiedCommittedLedgerState ofGenesisAncestor(LedgerState ledgerState) {
		BFTHeader header = BFTHeader.ofGenesisAncestor(ledgerState);
		return new VerifiedCommittedLedgerState(
			header,
			header,
			header,
			new TimestampedECDSASignatures()
		);
	}

	public LedgerState getRaw() {
		return header.getLedgerState();
	}

	public long getEpoch() {
		return header.getLedgerState().getEpoch();
	}

	public View getView() {
		return header.getLedgerState().getView();
	}

	public long getStateVersion() {
		return header.getLedgerState().getStateVersion();
	}

	public Hash getCommandId() {
		return header.getLedgerState().getCommandId();
	}

	public long timestamp() {
		return header.getLedgerState().timestamp();
	}

	public boolean isEndOfEpoch() {
		return header.getLedgerState().isEndOfEpoch();
	}

	@Override
	public int hashCode() {
		return Objects.hash(opaque0, opaque1, header, signatures);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof VerifiedCommittedLedgerState)) {
			return false;
		}

		VerifiedCommittedLedgerState other = (VerifiedCommittedLedgerState) o;
		return Objects.equals(this.opaque0, other.opaque0)
			&& Objects.equals(this.opaque1, other.opaque1)
			&& Objects.equals(this.header, other.header)
			&& Objects.equals(this.signatures, other.signatures);
	}

	@Override
	public String toString() {
		return String.format("%s{header=%s}", this.getClass().getSimpleName(), this.header);
	}

	@Override
	public int compareTo(VerifiedCommittedLedgerState o) {
		if (o.getEpoch() != this.getEpoch()) {
			return this.getEpoch() > o.getEpoch() ? 1 : -1;
		}

		if (o.getStateVersion() != this.getStateVersion()) {
			return this.getStateVersion() > o.getStateVersion() ? 1 : -1;
		}

		if (o.isEndOfEpoch() != this.isEndOfEpoch()) {
			return this.isEndOfEpoch() ? 1 : -1;
		}

		return 0;
	}
}
