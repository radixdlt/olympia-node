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

package com.radixdlt.integration.distributed.deterministic.network;

import java.util.concurrent.atomic.AtomicLong;

import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;

/**
 * A mutator called on new messages that can mutate the message, or the queue.
 * TODO: Could be broken up into a pipeline of filters and a single mutator.
 */
@FunctionalInterface
public interface MessageMutator {
	boolean mutate(MessageRank rank, ControlledMessage message, MessageQueue queue);

	default MessageMutator otherwise(MessageMutator next) {
		return (rank, message, queue) -> this.mutate(rank, message, queue) && next.mutate(rank, message, queue);
	}

	static MessageMutator addMessage() {
		return (rank, message, queue) -> queue.add(rank, message);
	}

	static MessageMutator stopAt(long messageCount) {
		AtomicLong counter = new AtomicLong(0L);
		return (rank, message, queue) -> counter.incrementAndGet() <= messageCount;
	}

	static MessageMutator stopAt(View view) {
		final long maxViewNumber = view.number();
		return (rank, message, queue) -> {
			Object m = message.message();
			if (m instanceof NewView) {
				NewView nv = (NewView) m;
				if (nv.getView().number() > maxViewNumber) {
					return false;
				}
			}
			return true;
		};
	}

	static MessageMutator stopAt(EpochView maxEpochView) {
		return (rank, message, queue) -> {
			Object m = message.message();
			if (m instanceof NewView) {
				NewView nv = (NewView) m;
				EpochView nev = EpochView.of(nv.getEpoch(), nv.getView());
				if (nev.compareTo(maxEpochView) > 0) {
					return false;
				}
			}
			return true;
		};
	}

	static MessageMutator alwaysAdd(long messageCount) {
		return stopAt(messageCount).otherwise(addMessage());
	}

	static MessageMutator alwaysAddUntilView(View maxView) {
		return stopAt(maxView).otherwise(addMessage());
	}

	static MessageMutator alwaysAddUntilEpochView(EpochView maxEpochView) {
		return stopAt(maxEpochView).otherwise(addMessage());
	}
}
