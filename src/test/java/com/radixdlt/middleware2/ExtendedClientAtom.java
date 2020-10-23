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
import com.radixdlt.serialization.SerializerId2;

@SerializerId2("extended_client_atom")
public class ExtendedClientAtom extends ClientAtom {
	@JsonProperty("extra")
	@DsonOutput({DsonOutput.Output.ALL})
	private final String extra;

	@JsonCreator
	private ExtendedClientAtom(
			@JsonProperty("aid") AID aid,
			@JsonProperty("metadata") String metaData,
			@JsonProperty("extra") String extra
	) {
		super(aid, metaData);
		this.extra = extra;
	}

	public static ExtendedClientAtom create(String metadata, String extra) {
		var id = AID.from(HashUtils.random(AID.BYTES).asBytes());
		return new ExtendedClientAtom(id, metadata, extra);
	}

	public String extra() {
		return extra;
	}

	@Override
	public boolean equals(Object o) {
		return super.equals(o) && (o instanceof ExtendedClientAtom) && (extra.equals(((ExtendedClientAtom) o).extra));
	}

	@Override
	public int hashCode() {
		return 31 * super.hashCode() + extra.hashCode();
	}

	@Override
	public String toString() {
		return "ExtendedClientAtom(" +
				"aid: " + aid() + ", " +
				"metaData: '" + metaData() + "', " +
				"extra: '" + extra + '\'' +
				')';
	}
}
