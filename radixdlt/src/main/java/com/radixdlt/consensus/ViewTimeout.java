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
import com.google.errorprone.annotations.Immutable;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;

/**
 * Represents a remote view timeout message from a validator.
 * <p>
 * A view timeout message signals to the receiver that a view timeout has
 * occurred for the author.
 */
@Immutable
@SerializerId2("consensus.view_timeout")
public final class ViewTimeout implements ConsensusEvent {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("view_timeout_data")
	@DsonOutput(Output.ALL)
	private final ViewTimeoutData viewTimeoutData;

	@JsonProperty("high_qc")
	@DsonOutput(Output.ALL)
	private final HighQC highQC;

	@JsonProperty("signature")
	@DsonOutput(Output.ALL)
	private final ECDSASignature signature;

	/**
	 * Creates a view timeout message from the specified arguments.
	 *
	 * @param viewTimeoutData The signed data indicating the view that timed out
	 * @param highQC Current synchronisation state for the author
	 * @param signature The signature covering {@code viewTimeoutData}
	 * @return A newly constructed {@code ViewTimeout}
	 */
	public static ViewTimeout from(ViewTimeoutData viewTimeoutData, HighQC highQC, ECDSASignature signature) {
		return new ViewTimeout(viewTimeoutData, highQC, signature);
	}

	@JsonCreator
	private ViewTimeout(
		@JsonProperty("view_timeout_data") ViewTimeoutData viewTimeoutData,
		@JsonProperty("high_qc") HighQC highQC,
		@JsonProperty("signature") ECDSASignature signature
	) {
		this.viewTimeoutData = Objects.requireNonNull(viewTimeoutData);
		this.highQC = Objects.requireNonNull(highQC);
		this.signature = Objects.requireNonNull(signature);
	}

	@Override
	public BFTNode getAuthor() {
		return this.viewTimeoutData.author();
	}

	@Override
	public long getEpoch() {
		return this.viewTimeoutData.epoch();
	}

	@Override
	public HighQC highQC() {
		return this.highQC;
	}

	@Override
	public View getView() {
		return this.viewTimeoutData.view();
	}

	public ViewTimeoutData viewTimeoutData() {
		return this.viewTimeoutData;
	}

	public ECDSASignature signature() {
		return this.signature;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o instanceof ViewTimeout) {
			ViewTimeout that = (ViewTimeout) o;
			return Objects.equals(this.viewTimeoutData, that.viewTimeoutData)
				&& Objects.equals(this.highQC, that.highQC)
				&& Objects.equals(this.signature, that.signature);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.viewTimeoutData, this.highQC, this.signature);
	}

	@Override
	public String toString() {
		return String.format("%s{author=%s epoch=%s view=%s %s}",
			getClass().getSimpleName(), getAuthor(), getEpoch(), getView(), highQC());
	}
}
