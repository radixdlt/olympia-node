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

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.common.AID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;

@Immutable
@SerializerId2("consensus.vertex_metadata")
public final class VertexMetadata {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	private Round round;

	@JsonProperty("aid")
	@DsonOutput(Output.ALL)
	private final AID aid;

	private Round parentRound;

	@JsonProperty("parent_aid")
	@DsonOutput(Output.ALL)
	private final AID parentAid;

	VertexMetadata() {
		// Serializer only
		this.round = null;
		this.aid = null;
		this.parentRound = null;
		this.parentAid = null;
	}

	public VertexMetadata(Round round, AID aid, Round parentRound, AID parentAid) {
		this.round = round;
		this.aid = aid;
		this.parentRound = parentRound;
		this.parentAid = parentAid;
	}

	public Round getRound() {
		return round;
	}

	public Round getParentRound() {
		return parentRound;
	}

	public AID getAID() {
		return aid;
	}

	public AID getParentAID() {
		return parentAid;
	}

	@JsonProperty("round")
	@DsonOutput(Output.ALL)
	private Long getSerializerRound() {
		return this.round == null ? null : this.round.number();
	}

	@JsonProperty("round")
	private void setSerializerRound(Long number) {
		this.round = number == null ? null : Round.of(number.longValue());
	}

	@JsonProperty("parent_round")
	@DsonOutput(Output.ALL)
	private Long getSerializerParentRound() {
		return this.parentRound == null ? null : this.parentRound.number();
	}

	@JsonProperty("parent_round")
	private void setSerializerParentRound(Long number) {
		this.parentRound = number == null ? null : Round.of(number.longValue());
	}


	@Override
	public int hashCode() {
		return Objects.hash(this.round, this.aid, this.parentRound, this.parentAid);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof VertexMetadata) {
			VertexMetadata other = (VertexMetadata) o;
			return
				Objects.equals(this.round, other.round)
				&& Objects.equals(this.aid, other.aid)
				&& Objects.equals(this.parentRound, other.parentRound)
				&& Objects.equals(this.parentAid, other.parentAid);
		}
		return false;
	}
}