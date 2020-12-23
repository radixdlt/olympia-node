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

	private MessageInTransit addLatencyIfNotToSelf(MessageInTransit msg, BFTNode receiver) {
		if (msg.getSender().equals(receiver)) {
			return msg;
		} else {
			return msg.delayed(latencyProvider.nextLatency(msg));
		}
	}

	public static Timed<MessageInTransit> delayCarryover(Timed<MessageInTransit> prev, Timed<MessageInTransit> next) {
		int delayCarryover = (int) Math.max(prev.time() + prev.value().getDelay() - next.time(), 0);
		int additionalDelay = (int) (next.value().getDelay() - delayCarryover);
		if (additionalDelay > 0) {
			return new Timed<>(next.value().delayAfterPrevious(additionalDelay), next.time(), next.unit());
		} else {
			return next;
		}
	}

	@Override
	public Observable<MessageInTransit> transform(BFTNode sender, BFTNode receiver, Observable<MessageInTransit> messages) {
		return messages
			.map(msg -> addLatencyIfNotToSelf(msg, receiver))
			.filter(msg -> msg.getDelay() >= 0)
			.timestamp(TimeUnit.MILLISECONDS)
			.scan(InOrderChannels::delayCarryover)
			.concatMap(p -> Observable.just(p.value()).delay(p.value().getDelayAfterPrevious(), TimeUnit.MILLISECONDS));
	}
}
