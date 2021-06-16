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

package com.radixdlt.network.messaging;

import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.utils.Bytes;

import java.util.Arrays;
import java.util.Objects;

/**
 * A raw message received from a peer, before decoding.
 */
public final class InboundMessage {
	private final NodeId source;
	private final byte[] message;

	/**
	 * Creates an inbound message with the specified source and message.
	 *
	 * @param source The source of the message.
	 * @param message The message received.
	 * @return a constructed {@code InboundMessage}
	 */
	public static InboundMessage of(NodeId source, byte[] message) {
		return new InboundMessage(source, message);
	}

	private InboundMessage(NodeId source, byte[] message) {
		// Null checking not performed for high-frequency interface
		this.source = source;
		this.message = message;
	}

	/**
	 * Returns the source of the message.
	 *
	 * @return the source of the message.
	 */
	public NodeId source() {
		return source;
	}

	/**
	 * Returns the message.
	 *
	 * @return the message.
	 */
	public byte[] message() {
		return message;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(message) * 31 + Objects.hashCode(source);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof InboundMessage) {
			final var other = (InboundMessage) obj;
			return Objects.equals(this.source, other.source) && Arrays.equals(this.message, other.message);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s]", getClass().getSimpleName(), source, Bytes.toHexString(message));
	}
}
