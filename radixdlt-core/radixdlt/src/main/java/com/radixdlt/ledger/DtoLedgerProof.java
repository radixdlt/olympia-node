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
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.TimestampedECDSASignatures;
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
@SerializerId2("ledger.dto_proof")
public final class DtoLedgerProof {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	// proposed
	@JsonProperty("opaque")
	@DsonOutput(Output.ALL)
	private final HashCode opaque;

	// committed ledgerState
	@JsonProperty("ledgerState")
	@DsonOutput(Output.ALL)
	private final LedgerHeader ledgerHeader;

	@JsonProperty("signatures")
	@DsonOutput(Output.ALL)
	private final TimestampedECDSASignatures signatures;

	@JsonCreator
	public DtoLedgerProof(
		@JsonProperty("opaque") HashCode opaque,
		@JsonProperty("ledgerState") LedgerHeader ledgerHeader,
		@JsonProperty("signatures") TimestampedECDSASignatures signatures
	) {
		this.opaque = Objects.requireNonNull(opaque);
		this.ledgerHeader = Objects.requireNonNull(ledgerHeader);
		this.signatures = Objects.requireNonNull(signatures);
	}

	public HashCode getOpaque() {
		return opaque;
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
		DtoLedgerProof that = (DtoLedgerProof) o;
		return Objects.equals(opaque, that.opaque)
			&& Objects.equals(ledgerHeader, that.ledgerHeader)
			&& Objects.equals(signatures, that.signatures);
	}

	@Override
	public int hashCode() {
		return Objects.hash(opaque, ledgerHeader, signatures);
	}
}
