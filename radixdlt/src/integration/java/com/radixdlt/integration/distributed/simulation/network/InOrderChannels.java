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
import io.reactivex.rxjava3.schedulers.Timed;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class InOrderChannels implements ChannelCommunication {
	private final LatencyProvider latencyProvider;

	@Inject
	public InOrderChannels(LatencyProvider latencyProvider) {
		this.latencyProvider = Objects.requireNonNull(latencyProvider);
	}

	@Override
	public Observable<MessageInTransit> transform(BFTNode sender, BFTNode receiver, Observable<MessageInTransit> messages) {
		return messages.map(msg -> {
			if (msg.getSender().equals(receiver)) {
				return msg;
			} else {
				return msg.delayed(latencyProvider.nextLatency(msg));
			}
		})
			.filter(msg -> msg.getDelay() >= 0)
			.timestamp(TimeUnit.MILLISECONDS)
			.scan((msg1, msg2) -> {
				int delayCarryover = (int) Math.max(msg1.time() + msg1.value().getDelay() - msg2.time(), 0);
				int additionalDelay = (int) (msg2.value().getDelay() - delayCarryover);
				if (additionalDelay > 0) {
					return new Timed<>(msg2.value().delayAfterPrevious(additionalDelay), msg2.time(), msg2.unit());
				} else {
					return msg2;
				}
			})
			.concatMap(p -> Observable.just(p.value()).delay(p.value().getDelayAfterPrevious(), TimeUnit.MILLISECONDS));
	}
}
