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

package com.radixdlt.mempool;

import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.network.addressbook.PeersView;

import javax.inject.Inject;
import java.util.Objects;

/**
 * Relays messages successfully added to the mempool to node neighbors.
 */
public final class MempoolRelayer {
	private final PeersView peersView;
	private final RemoteEventDispatcher<MempoolAddSuccess> remoteEventDispatcher;

	@Inject
	public MempoolRelayer(
		RemoteEventDispatcher<MempoolAddSuccess> remoteEventDispatcher,
		PeersView peersView
	) {
		this.remoteEventDispatcher = Objects.requireNonNull(remoteEventDispatcher);
		this.peersView = Objects.requireNonNull(peersView);
	}

	public EventProcessor<MempoolAddSuccess> mempoolAddedCommandEventProcessor() {
		return cmd -> this.peersView.peers()
			.stream().filter(p -> cmd.getOrigin().map(o -> !p.equals(o)).orElse(true))
			.forEach(peer -> this.remoteEventDispatcher.dispatch(peer, cmd));
	}
}
