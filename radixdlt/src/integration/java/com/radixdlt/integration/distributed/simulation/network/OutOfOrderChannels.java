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

package com.radixdlt.integration.distributed.simulation.network;

import com.google.inject.Inject;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork.ChannelCommunication;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork.MessageInTransit;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Provides message ordering in a channel which does not
 * necessarily need to be in order.
 */
public class OutOfOrderChannels implements ChannelCommunication {
	private final LatencyProvider latencyProvider;

	@Inject
	public OutOfOrderChannels(LatencyProvider latencyProvider) {
		this.latencyProvider = Objects.requireNonNull(latencyProvider);
	}

	@Override
	public Observable<MessageInTransit> transform(BFTNode sender, BFTNode receiver, Observable<MessageInTransit> messages) {
		return messages.map(msg -> msg.delayed(latencyProvider.nextLatency(msg)))
			.filter(msg -> msg.getDelay() >= 0)
			.flatMap(msg -> Observable.just(msg).delay(msg.getDelay(), TimeUnit.MILLISECONDS).observeOn(Schedulers.io()));
	}
}
