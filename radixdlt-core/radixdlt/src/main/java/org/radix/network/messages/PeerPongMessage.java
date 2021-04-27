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

import com.radixdlt.serialization.SerializerId2;
import org.radix.network.messaging.Message;

import java.util.Objects;

@SerializerId2("p2p.pong")
public final class PeerPongMessage extends Message {

	PeerPongMessage() {
		// for serializer
		super(0);
	}

	public PeerPongMessage(int magic) {
		super(magic);
	}

	@Override
	public String toString() {
		return String.format("%s[]", getClass().getSimpleName());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final var that = (PeerPongMessage) o;
		return Objects.equals(getTimestamp(), that.getTimestamp())
				&& Objects.equals(getMagic(), that.getMagic());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getTimestamp(), getMagic());
	}
}
