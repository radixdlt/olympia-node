/*
 * (C) Copyright 2021 Radix DLT Ltd
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

import java.util.Objects;

public final class MessageFromPeer<T> {
	private final NodeId source;
	private final T message;

	public MessageFromPeer(NodeId source, T message) {
		this.source = source;
		this.message = message;
	}

	public NodeId getSource() {
		return source;
	}

	public T getMessage() {
		return message;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		MessageFromPeer<?> that = (MessageFromPeer<?>) o;
		return Objects.equals(source, that.source)
				&& Objects.equals(message, that.message);
	}

	@Override
	public int hashCode() {
		return Objects.hash(source, message);
	}
}
