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

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;

/**
 * Select a message from a list of possible messages.
 */
@FunctionalInterface
public interface MessageSelector {

	/**
	 * Selects a message for processing from the supplied non-empty list.
	 * The supplied list is from a single rank, and is in arrival order.
	 *
	 * @param messages the messages to select from
	 * @return The selected message, or {@code null} if processing should stop
	 */
	ControlledMessage select(List<ControlledMessage> messages);

	/**
	 * Returns a selector that always returns the first message from the
	 * supplied list.
	 *
	 * @return a selector that always returns the first message from the
	 * 		supplied list
	 */
	static MessageSelector firstSelector() {
		return messages -> messages.get(0);
	}

	/**
	 * Returns a selector that returns a random message from the supplied list.
	 *
	 * @param random the random generator to use to select messages
	 * @return a selector that returns a random message from the supplied list
	 */
	static MessageSelector randomSelector(Random random) {
		return messages -> messages.get(random.nextInt(messages.size()));
	}

	/**
	 * Returns a selector that uses the supplied selector to select messages,
	 * but stops processing messages after the specified count have been
	 * processed.
	 *
	 * @param selector the selector to use
	 * @param messageCount the number of messages to process before stopping
	 * @return a selector that uses the specified selector, and halts
	 * 		processing after the specified number of messages
	 */
	static MessageSelector selectAndStopAfter(MessageSelector selector, long messageCount) {
		final AtomicLong counter = new AtomicLong(messageCount);
		return messageList -> {
			ControlledMessage message = selector.select(messageList);
			if (counter.getAndDecrement() == 0) {
				return null;
			}
			return message;
		};
	}

	/**
	 * Returns a selector that uses the supplied selector to select messages,
	 * but stops processing messages after a specified number of views.
	 *
	 * @param selector the selector to use
	 * @param view the last view to process
	 * @return a selector that uses the specified selector, and halts
	 * 		processing after the specified number of views
	 */
	static MessageSelector selectAndStopAt(MessageSelector selector, View view) {
		final long maxViewNumber = view.number();
		return messageList -> {
			ControlledMessage message = selector.select(messageList);
			if (message == null || !(message.message() instanceof NewView)) {
				return message;
			}
			NewView nv = (NewView) message.message();
			return (nv.getView().number() > maxViewNumber) ? null : message;
		};
	}

	/**
	 * Returns a selector that uses the supplied selector to select messages,
	 * but stops processing messages after a specified number of epochs and
	 * views.
	 *
	 * @param selector the selector to use
	 * @param view the last view to process
	 * @return a selector that uses the specified selector, and halts
	 * 		processing after the specified number of epochs and views
	 */
	static MessageSelector selectAndStopAt(MessageSelector selector, EpochView maxEpochView) {
		return messageList -> {
			ControlledMessage message = selector.select(messageList);
			if (message == null || !(message.message() instanceof Proposal)) {
				return message;
			}
			Proposal p = (Proposal) message.message();
			EpochView nev = EpochView.of(p.getEpoch(), p.getVertex().getView());
			return (nev.compareTo(maxEpochView) > 0) ? null : message;
		};
	}
}
