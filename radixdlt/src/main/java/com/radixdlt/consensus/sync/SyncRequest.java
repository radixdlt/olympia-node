/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.consensus.sync;

import com.radixdlt.network.addressbook.Peer;
import java.util.Objects;

/**
 * A sync request from a peer
 */
public final class SyncRequest {
	private final long stateVersion;
	private final Peer peer;

	public SyncRequest(Peer peer, long stateVersion) {
		this.peer = Objects.requireNonNull(peer);
		this.stateVersion = stateVersion;
	}

	public Peer getPeer() {
		return peer;
	}

	public long getStateVersion() {
		return stateVersion;
	}

	@Override
	public String toString() {
		return String.format("%s{stateVersion=%s}", this.getClass().getSimpleName(), stateVersion);
	}
}
