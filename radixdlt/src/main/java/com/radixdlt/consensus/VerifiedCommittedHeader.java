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
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;

@Immutable
@SerializerId2("ledger.verified_committed_header")
public final class VerifiedCommittedHeader {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("opaque0")
	@DsonOutput(Output.ALL)
	private final CommandHeader opaque0;

	@JsonProperty("opaque1")
	@DsonOutput(Output.ALL)
	private final CommandHeader opaque1;

	@JsonProperty("header")
	@DsonOutput(Output.ALL)
	private final CommandHeader header;

	@JsonProperty("signatures")
	@DsonOutput(Output.ALL)
	private final TimestampedECDSASignatures signatures;

	@JsonCreator
	public VerifiedCommittedHeader(
		@JsonProperty("opaque0") CommandHeader opaque0,
		@JsonProperty("opaque1") CommandHeader opaque1,
		@JsonProperty("header") CommandHeader header,
		@JsonProperty("signatures") TimestampedECDSASignatures signatures
	) {
		this.opaque0 = Objects.requireNonNull(opaque0);
		this.opaque1 = Objects.requireNonNull(opaque1);
		this.header = Objects.requireNonNull(header);
		this.signatures = Objects.requireNonNull(signatures);
	}

	public CommandHeader getHeader() {
		return header;
	}

	@Override
	public int hashCode() {
		return Objects.hash(opaque0, opaque1, header, signatures);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof VerifiedCommittedHeader)) {
			return false;
		}

		VerifiedCommittedHeader other = (VerifiedCommittedHeader) o;
		return Objects.equals(this.opaque0, other.opaque0)
			&& Objects.equals(this.opaque1, other.opaque1)
			&& Objects.equals(this.header, other.header)
			&& Objects.equals(this.signatures, other.signatures);
	}
}
