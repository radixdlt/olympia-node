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
import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.HashUtils;
import com.google.common.hash.HashCode;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.DtoLedgerProof;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.concurrent.Immutable;

/**
 * Ledger header with proof
 */
@Immutable
@SerializerId2("ledger.proof")
public final class LedgerProof {
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
	public LedgerProof(
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

	public static LedgerProof genesis(AccumulatorState accumulatorState, BFTValidatorSet nextValidators, long timestamp) {
		LedgerHeader genesisLedgerHeader = LedgerHeader.genesis(accumulatorState, nextValidators, timestamp);
		BFTHeader header = BFTHeader.ofGenesisAncestor(genesisLedgerHeader);
		return new LedgerProof(
			header,
			header,
			0,
			HashUtils.zero256(),
			genesisLedgerHeader,
			new TimestampedECDSASignatures()
		);
	}

	public static final class OrderByEpochAndVersionComparator implements Comparator<LedgerProof> {
		@Override
		public int compare(LedgerProof p0, LedgerProof p1) {
			if (p0.ledgerHeader.getEpoch() != p1.ledgerHeader.getEpoch()) {
				return p0.ledgerHeader.getEpoch() > p1.ledgerHeader.getEpoch() ? 1 : -1;
			}

			if (p0.ledgerHeader.isEndOfEpoch() != p1.ledgerHeader.isEndOfEpoch()) {
				return p0.ledgerHeader.isEndOfEpoch() ? 1 : -1;
			}

			return Long.compare(
				p0.ledgerHeader.getAccumulatorState().getStateVersion(),
				p1.ledgerHeader.getAccumulatorState().getStateVersion()
			);
		}
	}

	public DtoLedgerProof toDto() {
		return new DtoLedgerProof(
			opaque0,
			opaque1,
			opaque2,
			opaque3,
			ledgerHeader,
			signatures
		);
	}

	public LedgerHeader getRaw() {
		return ledgerHeader;
	}

	public Optional<BFTValidatorSet> getNextValidatorSet() {
		return ledgerHeader.getNextValidatorSet();
	}

	public long getEpoch() {
		return ledgerHeader.getEpoch();
	}

	public View getView() {
		return ledgerHeader.getView();
	}

	public AccumulatorState getAccumulatorState() {
		return ledgerHeader.getAccumulatorState();
	}

	// TODO: Remove
	public long getStateVersion() {
		return ledgerHeader.getAccumulatorState().getStateVersion();
	}

	public long timestamp() {
		return ledgerHeader.timestamp();
	}

	public boolean isEndOfEpoch() {
		return ledgerHeader.isEndOfEpoch();
	}

	public TimestampedECDSASignatures getSignatures() {
		return signatures;
	}

	public ImmutableList<BFTNode> getSignersWithout(BFTNode remove) {
		return signatures.getSignatures().keySet().stream()
			.filter(n -> !n.equals(remove))
			.collect(ImmutableList.toImmutableList());
	}

	@Override
	public int hashCode() {
		return Objects.hash(opaque0, opaque1, opaque2, opaque3, ledgerHeader, signatures);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof LedgerProof)) {
			return false;
		}

		LedgerProof other = (LedgerProof) o;
		return Objects.equals(this.opaque0, other.opaque0)
			&& Objects.equals(this.opaque1, other.opaque1)
			&& this.opaque2 == other.opaque2
			&& Objects.equals(this.opaque3, other.opaque3)
			&& Objects.equals(this.ledgerHeader, other.ledgerHeader)
			&& Objects.equals(this.signatures, other.signatures);
	}

	@Override
	public String toString() {
		return String.format("%s{ledger=%s}", this.getClass().getSimpleName(), this.ledgerHeader);
	}
}
