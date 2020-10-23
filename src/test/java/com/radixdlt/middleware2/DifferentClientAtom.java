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

package com.radixdlt.middleware2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.AID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

@SerializerId2("different_client_atom")
public class DifferentClientAtom implements LedgerAtom {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {DsonOutput.Output.API, DsonOutput.Output.WIRE, DsonOutput.Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("datameta")
	@DsonOutput({DsonOutput.Output.ALL})
	private final String metaData;

	@JsonProperty("dia")
	@DsonOutput({DsonOutput.Output.ALL})
	private final AID aid;

	@JsonCreator
	private DifferentClientAtom(
			@JsonProperty("dia") AID aid,
			@JsonProperty("datameta") String metaData
	) {
		this.aid = aid;
		this.metaData = metaData == null ? "no metadata" : metaData;
	}

	public static DifferentClientAtom create(String metadata) {
		var id = AID.from(HashUtils.random(AID.BYTES).asBytes());
		return new DifferentClientAtom(id, metadata);
	}

	public AID aid() {
		return aid;
	}

	public String metaData() {
		return metaData;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof DifferentClientAtom)) {
			return false;
		}

		DifferentClientAtom that = (DifferentClientAtom) o;

		if (!metaData.equals(that.metaData)) {
			return false;
		}

		return aid.equals(that.aid);
	}

	@Override
	public int hashCode() {
		int result = metaData.hashCode();
		result = 31 * result + aid.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return "DifferentClientAtom(metaData: '" + metaData + "', aid: " + aid + ')';
	}
}
