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

package com.radixdlt.ledger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;

/**
 * A ledger header and proof which has not been verified
 */
@Immutable
@SerializerId2("ledger.ledger_header_and_proof")
public final class DtoLedgerHeaderAndProof {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	// proposed
	@JsonProperty("opaque0")
	@DsonOutput(Output.ALL)
	private final BFTHeader opaque0;

	// parent
	@JsonProperty("opaque1")
	@DsonOutput(Output.ALL)
	private final BFTHeader opaque1;

	// committed view
	@JsonProperty("opaque2")
	@DsonOutput(Output.ALL)
	private final long opaque2;

	// committed vertexId
	@JsonProperty("opaque3")
	@DsonOutput(Output.ALL)
	private final HashCode opaque3;

	// committed ledgerState
	@JsonProperty("ledgerState")
	@DsonOutput(Output.ALL)
	private final LedgerHeader ledgerHeader;

	@JsonProperty("signatures")
	@DsonOutput(Output.ALL)
	private final TimestampedECDSASignatures signatures;

	@JsonCreator
	public DtoLedgerHeaderAndProof(
		@JsonProperty("opaque0") BFTHeader opaque0,
		@JsonProperty("opaque1") BFTHeader opaque1,
		@JsonProperty("opaque2") long opaque2,
		@JsonProperty("opaque3") HashCode opaque3,
		@JsonProperty("ledgerState") LedgerHeader ledgerHeader,
		@JsonProperty("signatures") TimestampedECDSASignatures signatures
	) {
		this.opaque0 = Objects.requireNonNull(opaque0);
		this.opaque1 = Objects.requireNonNull(opaque1);
		this.opaque2 = opaque2;
		this.opaque3 = Objects.requireNonNull(opaque3);
		this.ledgerHeader = Objects.requireNonNull(ledgerHeader);
		this.signatures = Objects.requireNonNull(signatures);
	}

	public VoteData toVoteData() {
		return new VoteData(
			this.opaque0,
			this.opaque1,
			new BFTHeader(
				View.of(this.opaque2),
				this.opaque3,
				this.ledgerHeader
			)
		);
	}

	public BFTHeader getOpaque0() {
		return opaque0;
	}

	public BFTHeader getOpaque1() {
		return opaque1;
	}

	public long getOpaque2() {
		return opaque2;
	}

	public HashCode getOpaque3() {
		return opaque3;
	}

	public TimestampedECDSASignatures getSignatures() {
		return signatures;
	}

	public LedgerHeader getLedgerHeader() {
		return ledgerHeader;
	}

	@Override
	public String toString() {
		return String.format("%s{header=%s}", this.getClass().getSimpleName(), this.ledgerHeader);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		DtoLedgerHeaderAndProof that = (DtoLedgerHeaderAndProof) o;
		return opaque2 == that.opaque2
				&& Objects.equals(opaque0, that.opaque0)
				&& Objects.equals(opaque1, that.opaque1)
				&& Objects.equals(opaque3, that.opaque3)
				&& Objects.equals(ledgerHeader, that.ledgerHeader)
				&& Objects.equals(signatures, that.signatures);
	}

	@Override
	public int hashCode() {
		return Objects.hash(opaque0, opaque1, opaque2, opaque3, ledgerHeader, signatures);
	}
}
