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

package org.radix.serialization;

import com.google.common.hash.HashCode;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import com.radixdlt.identifiers.EUID;
import org.radix.containers.BasicContainer;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.utils.UInt128;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A dummy object containing various primitives to test
 * serialization / deserialization speed
 */
@SerializerId2("test.dummy_test_object_1")
public final class DummyTestObject extends BasicContainer {
	private static final Random r = new Random(0);
	private static final byte[] randomData;

	static {
		randomData = new byte[0x40];
		r.nextBytes(randomData);
	}

	@JsonProperty("btrue")
	@DsonOutput(Output.ALL)
	private boolean btrue;

	@JsonProperty("bfalse")
	@DsonOutput(Output.ALL)
	private boolean bfalse;

	@JsonProperty("num")
	@DsonOutput(Output.ALL)
	private long num;

	@JsonProperty("id")
	@DsonOutput(Output.ALL)
	private EUID id;

	@JsonProperty("theHash")
	@DsonOutput(Output.ALL)
	private HashCode theHash;

	@JsonProperty("bytes")
	@DsonOutput(Output.ALL)
	private byte[] bytes;

	@JsonProperty("string")
	@DsonOutput(Output.ALL)
	private String string;

	@JsonProperty("array")
	@DsonOutput(Output.ALL)
	private List<EUID> array;

	@JsonProperty("object")
	@DsonOutput(Output.ALL)
	private DummyTestObject2 object;

	public DummyTestObject() {
		this(false);
	}

	public DummyTestObject(boolean initData) {
		if (initData) {
			this.btrue = true;
			this.bfalse = false;
			this.num = 0x123456789abcdefL;
			this.id = new EUID(UInt128.from(r.nextLong(), r.nextLong()));
			this.theHash = HashUtils.sha256(randomData);
			this.bytes = randomData.clone();
			this.string = getClass().getName();
			this.array = new ArrayList<>(Collections.nCopies(10, id));
			this.object = new DummyTestObject2();
		}
	}

	@Override
	public short VERSION() {
		return 100;
	}

	@Override
	public int hashCode() {
		return Objects.hash(btrue, bfalse, num, id, theHash, string, array, object) * 31 + Arrays.hashCode(bytes);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj instanceof DummyTestObject) {
			DummyTestObject other = (DummyTestObject) obj;
			return
					this.btrue == other.btrue &&
					this.bfalse == other.bfalse &&
					this.num == other.num &&
					Objects.equals(this.id, other.id) &&
					Objects.equals(this.theHash, other.theHash) &&
					Objects.equals(this.string, other.string) &&
					Arrays.equals(this.bytes, other.bytes) &&
					Objects.equals(this.array, other.array) &&
					Objects.equals(this.object, other.object);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format(
				"%s[btrue=%s, bfalse=%s, num=%s, id=%s, theHash=%s, bytes=%s, string=%s, array=%s, object=%s]",
				getClass().getSimpleName(), btrue, bfalse, num, id, theHash, Arrays.toString(bytes), string, array, object);
	}

}
