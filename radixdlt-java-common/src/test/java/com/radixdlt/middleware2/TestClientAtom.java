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
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

@SerializerId2("client_atom")
public class TestClientAtom implements TestLedgerAtom {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput({Output.ALL})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("metadata")
	@DsonOutput({Output.ALL})
	private final String metaData;

	@JsonProperty("aid")
	@DsonOutput({Output.ALL})
	private final AID aid;

	@JsonCreator
	protected TestClientAtom(
			@JsonProperty("aid") AID aid,
			@JsonProperty("metadata") String metaData
	) {
		this.aid = aid;
		this.metaData = metaData == null ? "no metadata" : metaData;
	}

	public static TestClientAtom create(String metadata) {
		var id = AID.from(HashUtils.random(AID.BYTES).asBytes());
		return new TestClientAtom(id, metadata);
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

		if (!(o instanceof TestClientAtom)) {
			return false;
		}

		TestClientAtom that = (TestClientAtom) o;

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
		return "ClientAtom(metaData: '" + metaData + "', aid: " + aid + ')';
	}
}
