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

package com.radixdlt.environment.deterministic.network;

import com.radixdlt.consensus.epoch.LocalViewUpdate;

import java.util.List;
import java.util.Random;

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
	 * Returns a new MessageSelector which ensures that LocalViewUpdates get
	 * processed before any other messages.
	 * Doesn't change the selection rules for other message types.
	 */
	default MessageSelector viewUpdatesFirst() {
		return messages -> {
			for (ControlledMessage message: messages) {
				if (message.message() instanceof LocalViewUpdate) {
					return message;
				}
			}
			return this.select(messages);
		};
	}

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
}
