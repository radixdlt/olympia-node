/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.sync.messages.local;

import com.radixdlt.consensus.bft.BFTNode;

import java.util.Objects;

/**
 * A message indicating a timeout on receiving a SyncResponse message.
 */
public final class SyncRequestTimeout {

	private final BFTNode peer;
	private final long requestId;

	public static SyncRequestTimeout create(BFTNode peer, long requestId) {
		return new SyncRequestTimeout(peer, requestId);
	}

	private SyncRequestTimeout(BFTNode peer, long requestId) {
		this.peer = peer;
		this.requestId = requestId;
	}

	public BFTNode getPeer() {
		return peer;
	}

	public long getRequestId() {
		return requestId;
	}

	@Override
	public String toString() {
		return String.format("%s{peer=%s requestId=%s}",
			this.getClass().getSimpleName(), peer, requestId);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		SyncRequestTimeout that = (SyncRequestTimeout) o;
		return Objects.equals(peer, that.peer)
			&& requestId == that.requestId;
	}

	@Override
	public int hashCode() {
		return Objects.hash(peer, requestId);
	}
}
