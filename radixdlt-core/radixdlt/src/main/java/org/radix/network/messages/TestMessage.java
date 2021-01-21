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

package org.radix.network.messages;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

import org.radix.network.messaging.Message;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

// CHECKSTYLE:OFF checkstyle:VisibilityModifier
@SerializerId2("network.message.test")
public final class TestMessage extends Message {
	@JsonProperty("nonce")
	@DsonOutput(Output.ALL)
	public long testnonce;

	@JsonProperty("junk")
	@DsonOutput(Output.ALL)
	public byte[] junk;

	TestMessage() {
		// for serializer
		this(0);
	}

	public TestMessage(int magic) {
		super(magic);
		testnonce = new SecureRandom().nextLong();
		junk = new byte[1000];
	}

	public TestMessage(int magic, long timestamp) {
		super(magic, timestamp);
		testnonce = new SecureRandom().nextLong();
		junk = new byte[1000];
	}

	public TestMessage(final int size, int magic) {
		super(magic);
		testnonce = new SecureRandom().nextLong();
		junk = new byte[size];
	}

	public long getTestNonce() {
		return testnonce;
	}

	public void setTestNonce(long testnonce) {
		this.testnonce = testnonce;
	}

	public byte[] getJunk() {
		return junk;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		TestMessage that = (TestMessage) o;
		return testnonce == that.testnonce
				&& Arrays.equals(junk, that.junk)
				&& Objects.equals(getTimestamp(), that.getTimestamp())
				&& Objects.equals(getMagic(), that.getMagic());
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(testnonce, getTimestamp(), getMagic());
		result = 31 * result + Arrays.hashCode(junk);
		return result;
	}
}
// CHECKSTYLE:ON checkstyle:VisibilityModifier