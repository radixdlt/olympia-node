/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Represents a timeout certificate for given epoch and view signed by the quorum of validators.
 */
@SerializerId2("consensus.tc")
public final class TimeoutCertificate {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("epoch")
	@DsonOutput(Output.ALL)
	private final long epoch;

	private final View view;

	@JsonProperty("signatures")
	@DsonOutput(Output.ALL)
	private final TimestampedECDSASignatures signatures;

	@JsonCreator
	private static TimeoutCertificate serializerCreate(
		@JsonProperty("epoch") long epoch,
		@JsonProperty("view") Long view,
		@JsonProperty("signatures") TimestampedECDSASignatures signatures
	) {
		return new TimeoutCertificate(epoch, View.of(view), signatures);
	}

	public TimeoutCertificate(long epoch, View view, TimestampedECDSASignatures signatures) {
		this.epoch = epoch;
		this.view = Objects.requireNonNull(view);
		this.signatures = Objects.requireNonNull(signatures);
	}

	public long getEpoch() {
		return this.epoch;
	}

	public View getView() {
		return this.view;
	}

	public TimestampedECDSASignatures getTimestampedSignatures() {
		return signatures;
	}

	public Stream<BFTNode> getSigners() {
		return signatures.getSignatures().keySet().stream();
	}

	@JsonProperty("view")
	@DsonOutput(DsonOutput.Output.ALL)
	private Long getSerializerView() {
		return this.view.number();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		TimeoutCertificate that = (TimeoutCertificate) o;
		return Objects.equals(signatures, that.signatures)
			&& epoch == that.epoch
			&& Objects.equals(view, that.view);
	}

	@Override
	public int hashCode() {
		return Objects.hash(signatures, epoch, view);
	}

	@Override
	public String toString() {
		return String.format("TC{view=%s epoch=%s}", view, epoch);
	}
}
