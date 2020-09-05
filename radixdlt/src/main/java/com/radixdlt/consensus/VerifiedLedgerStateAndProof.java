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
@SerializerId2("ledger.verified_ledger_state_and_proof")
public final class VerifiedLedgerStateAndProof implements Comparable<VerifiedLedgerStateAndProof> {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("opaque0")
	@DsonOutput(Output.ALL)
	private final BFTHeader opaque0;

	@JsonProperty("opaque1")
	@DsonOutput(Output.ALL)
	private final BFTHeader opaque1;

	@JsonProperty("opaque2")
	@DsonOutput(Output.ALL)
	private final long opaque2;

	@JsonProperty("opaque3")
	@DsonOutput(Output.ALL)
	private final Hash opaque3;

	@JsonProperty("ledgerState")
	@DsonOutput(Output.ALL)
	private final LedgerState ledgerState;

	@JsonProperty("signatures")
	@DsonOutput(Output.ALL)
	private final TimestampedECDSASignatures signatures;

	@JsonCreator
	public VerifiedLedgerStateAndProof(
		@JsonProperty("opaque0") BFTHeader opaque0,
		@JsonProperty("opaque1") BFTHeader opaque1,
		@JsonProperty("opaque2") long opaque2,
		@JsonProperty("opaque3") Hash opaque3,
		@JsonProperty("ledgerState") LedgerState ledgerState,
		@JsonProperty("signatures") TimestampedECDSASignatures signatures
	) {
		this.opaque0 = Objects.requireNonNull(opaque0);
		this.opaque1 = Objects.requireNonNull(opaque1);
		this.opaque2 = opaque2;
		this.opaque3 = Objects.requireNonNull(opaque3);
		this.ledgerState = Objects.requireNonNull(ledgerState);
		this.signatures = Objects.requireNonNull(signatures);
	}

	public static VerifiedLedgerStateAndProof ofGenesisAncestor(LedgerState ledgerState) {
		BFTHeader header = BFTHeader.ofGenesisAncestor(ledgerState);
		return new VerifiedLedgerStateAndProof(
			header,
			header,
			0,
			Hash.ZERO_HASH,
			ledgerState,
			new TimestampedECDSASignatures()
		);
	}

	public LedgerState getRaw() {
		return ledgerState;
	}

	public long getEpoch() {
		return ledgerState.getEpoch();
	}

	public View getView() {
		return ledgerState.getView();
	}

	public long getStateVersion() {
		return ledgerState.getStateVersion();
	}

	public Hash getCommandId() {
		return ledgerState.getCommandId();
	}

	public long timestamp() {
		return ledgerState.timestamp();
	}

	public boolean isEndOfEpoch() {
		return ledgerState.isEndOfEpoch();
	}

	@Override
	public int hashCode() {
		return Objects.hash(opaque0, opaque1, opaque2, opaque3, ledgerState, signatures);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof VerifiedLedgerStateAndProof)) {
			return false;
		}

		VerifiedLedgerStateAndProof other = (VerifiedLedgerStateAndProof) o;
		return Objects.equals(this.opaque0, other.opaque0)
			&& Objects.equals(this.opaque1, other.opaque1)
			&& this.opaque2 == other.opaque2
			&& Objects.equals(this.opaque3, other.opaque3)
			&& Objects.equals(this.ledgerState, other.ledgerState)
			&& Objects.equals(this.signatures, other.signatures);
	}

	@Override
	public String toString() {
		return String.format("%s{ledger=%s}", this.getClass().getSimpleName(), this.ledgerState);
	}

	@Override
	public int compareTo(VerifiedLedgerStateAndProof o) {
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
