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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.primitives.UnsignedLong;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import org.radix.universe.system.RadixSystem;
import org.radix.universe.system.SystemMessage;

@SerializerId2("peer.pong")
public final class PeerPongMessage extends SystemMessage {
	@JsonProperty("nonce")
	@DsonOutput(Output.ALL)
	private long nonce;

	@JsonProperty("payload")
	@DsonOutput(Output.ALL)
	private long payload;

	PeerPongMessage() {
		// for serializer
	}

	public PeerPongMessage(int magic, long nonce, long payload, RadixSystem system) {
		super(system, magic);

		this.nonce = nonce;
		this.payload = payload;
	}

	public long getNonce() {
		return nonce;
	}

	public long getPayload() {
		return payload;
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s:%s]",
			getClass().getSimpleName(), getSystem().getNID(), UnsignedLong.fromLongBits(nonce), UnsignedLong.fromLongBits(payload));
	}
}
